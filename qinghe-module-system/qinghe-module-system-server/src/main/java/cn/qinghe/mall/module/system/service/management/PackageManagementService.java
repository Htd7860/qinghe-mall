package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages.*;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.*;
import cn.qinghe.mall.module.system.dal.mysql.tenant.*;
import cn.qinghe.mall.module.system.service.management.TenantRoleMenuSynchronizer.RolePlan;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

@Service
@RequiredArgsConstructor
public class PackageManagementService {
    private final ManagementTransactions transactions;
    private final TenantPackageMapper packageMapper;
    private final TenantMapper tenantMapper;
    private final TenantPackageChangeMapper changeMapper;
    private final ManagementMenuCatalog catalog;
    private final TenantRoleMenuSynchronizer synchronizer;
    private final ManagementAudit audit;

    public PageResult<PackageRespVO> page(PackagePageReqVO query) {
        ManagementTransactions.requirePlatformRead();Page<TenantPackageDO> page=MybatisUtils.buildPage(query);
        page.setRecords(packageMapper.selectManagementPage(page,query));
        return new PageResult<>(page.getTotal(),page.getRecords().stream().map(PackageRespVO::from).collect(Collectors.toList()));
    }
    public PackageRespVO get(Long id) {
        ManagementTransactions.requirePlatformRead();TenantPackageDO row=packageMapper.selectLifecycleDetail(id);
        if(row==null) { throw exception0(404,"套餐不存在"); }return PackageRespVO.from(row);
    }
    public Long create(PackageSaveReqVO body) {
        return transactions.execute("system:tenant-package:create",actor -> {
            actor.requirePlatform();
            if(body.getId()!=null || body.getExpectedVersion()!=null || body.getOperationId()!=null || body.getImpactToken()!=null) {
                throw exception0(400,"创建套餐不能指定编号、版本或变更结果标识");
            }
            Set<Long> menus=catalog.validateSelection("merchant",body.getMenuIds(),Collections.emptySet());
            TenantPackageDO row=new TenantPackageDO();row.setName(body.getName());row.setStatus(body.getStatus());row.setRemark(body.getRemark());
            row.setMenuIds(menus);row.setAuthVersion(0L);row.setDeleted(false);
            row.setCreator(actor.getId().toString());row.setUpdater(actor.getId().toString());row.setCreateTime(actor.getNow());row.setUpdateTime(actor.getNow());
            if(packageMapper.insert(row)!=1 || row.getId()==null || row.getId()<=0) { throw new IllegalStateException("套餐创建结果异常"); }
            audit.record(actor,0L,"PACKAGE.CREATE",row.getId(),-1L,0L,Collections.singletonMap("menuIds",menus));return row.getId();
        });
    }
    public PackageImpactRespVO preview(PackagePreviewReqVO body) {
        return transactions.execute("system:tenant-package:update",actor -> {
            actor.requirePlatform();return load(body.getId(),body.getExpectedVersion(),body.getMenuIds(),actor).preview;
        });
    }
    public PackageChangeRespVO update(PackageSaveReqVO body) {
        return transactions.execute("system:tenant-package:update",actor -> {
            actor.requirePlatform();requireOperationId(body.getOperationId());
            String requestHash=requestHash(body);
            TenantPackageChangeDO prior=findChange(body.getOperationId());
            if(prior!=null) {
                if(!actor.getId().equals(prior.getActorUserId()) || !requestHash.equals(prior.getRequestHash())) {
                    throw exception0(409,"该操作编号已用于其他提交，请重新读取后发起新操作");
                }
                return PackageChangeRespVO.from(prior);
            }
            Context context=load(body.getId(),body.getExpectedVersion(),body.getMenuIds(),actor);
            TenantPackageDO row=context.pack;
            boolean resourcesChanged=!Objects.equals(row.getMenuIds(),context.target);
            if(resourcesChanged && !Objects.equals(context.preview.getImpactToken(),body.getImpactToken())) {
                throw exception0(409,"套餐影响范围已变化，请重新预览并确认");
            }
            boolean changed=resourcesChanged || !Objects.equals(row.getName(),body.getName())
                    || !Objects.equals(row.getStatus(),body.getStatus()) || !Objects.equals(row.getRemark(),body.getRemark());
            if(changed) {
                if(packageMapper.updateConfiguration(row.getId(),row.getAuthVersion(),body.getName(),body.getStatus(),
                        body.getRemark(),context.target,actor.getId().toString(),actor.getNow())!=1) {
                    throw exception0(409,"套餐已变化，请重新读取");
                }
                for(int i=0;i<context.tenants.size();i++) {
                    TenantDO tenant=context.tenants.get(i);
                    synchronizer.apply(tenant,context.roles.get(i),actor);
                    // 包含逻辑删除租户，故使用显式SQL更新其配置版本。
                    if(tenantMapper.incrementPackageVersion(tenant.getId(),tenant.getAuthVersion(),actor.getId().toString(),actor.getNow())!=1) {
                        throw exception0(409,"受影响租户已变化，请重新预览");
                    }
                    Map<String,Object> details=new LinkedHashMap<>();details.put("packageId",row.getId());details.put("operationId",body.getOperationId());
                    audit.record(actor,tenant.getId(),"TENANT.PACKAGE_SYNC",tenant.getId(),tenant.getAuthVersion(),tenant.getAuthVersion()+1,details);
                }
                Map<String,Object> details=summary(context.preview);details.put("operationId",body.getOperationId());
                audit.record(actor,0L,"PACKAGE.UPDATE",row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);
            }
            TenantPackageChangeDO result=new TenantPackageChangeDO();result.setOperationId(body.getOperationId());result.setPackageId(row.getId());
            result.setActorUserId(actor.getId());result.setRequestHash(requestHash);result.setFromVersion(row.getAuthVersion());
            result.setToVersion(row.getAuthVersion()+(changed?1:0));result.setChanged(changed);result.setImpact(summary(context.preview));result.setCreateTime(actor.getNow());
            if(changeMapper.insert(result)!=1) { throw new IllegalStateException("套餐变更结果保存异常"); }
            return PackageChangeRespVO.from(result);
        });
    }
    public PackageChangeRespVO getChange(String operationId) {
        ManagementTransactions.requirePlatformRead();requireOperationId(operationId);TenantPackageChangeDO row=findChange(operationId);
        if(row==null) { return null; } // 未确认，不解释为失败或成功；可用同一operationId重新提交确认。
        if(!SecurityFrameworkUtils.getLoginUser().getId().equals(row.getActorUserId())) { throw exception0(404,"操作结果不存在"); }
        return PackageChangeRespVO.from(row);
    }
    public List<PackageChangeRespVO> history(Long packageId) {
        ManagementTransactions.requirePlatformRead();
        return changeMapper.selectList(new LambdaQueryWrapper<TenantPackageChangeDO>().eq(TenantPackageChangeDO::getPackageId,packageId)
                .orderByDesc(TenantPackageChangeDO::getId).last("LIMIT 20")).stream().map(PackageChangeRespVO::from).collect(Collectors.toList());
    }
    public Long status(VersionedStatusReqVO body) {
        return transactions.execute("system:tenant-package:update",actor -> {
            actor.requirePlatform();TenantPackageDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(Objects.equals(row.getStatus(),body.getStatus())) { return row.getAuthVersion(); }
            if(Integer.valueOf(0).equals(body.getStatus())) { catalog.validateSelection("merchant",row.getMenuIds(),row.getMenuIds()); }
            requireChanged(packageMapper.update(null,change(row,actor).set(TenantPackageDO::getStatus,body.getStatus())));
            return recorded(actor,row,"PACKAGE.STATUS",Collections.singletonMap("status",body.getStatus()));
        });
    }
    public Long delete(VersionedIdReqVO body) {
        return transactions.execute("system:tenant-package:delete",actor -> {
            actor.requirePlatform();TenantPackageDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(tenantMapper.countAllByPackage(row.getId())>0) { throw exception0(409,"套餐仍被租户引用（包含可恢复租户），不能删除"); }
            requireChanged(packageMapper.update(null,change(row,actor).set(TenantPackageDO::getDeleted,true).set(TenantPackageDO::getStatus,1)));
            return recorded(actor,row,"PACKAGE.DELETE",Collections.singletonMap("referencesChecked",true));
        });
    }
    public Long restore(VersionedIdReqVO body) {
        return transactions.execute("system:tenant-package:restore",actor -> {
            actor.requirePlatform();TenantPackageDO row=editable(body.getId(),body.getExpectedVersion(),true);
            catalog.validateSelection("merchant",row.getMenuIds(),row.getMenuIds());
            requireChanged(packageMapper.restoreLifecycle(row.getId(),row.getAuthVersion(),actor.getId().toString(),actor.getNow()));
            return recorded(actor,row,"PACKAGE.RESTORE",Collections.singletonMap("disabled",true));
        });
    }
    private Context load(Long id,Long version,Set<Long> requested,ManagementTransactions.Actor actor) {
        requireVersion(id,version);
        TenantPackageDO initial=packageMapper.selectLifecycleDetail(id);
        if(initial==null || Boolean.TRUE.equals(initial.getDeleted())) { throw exception0(404,"套餐不存在"); }
        boolean resourcesChanged=!Objects.equals(initial.getMenuIds(),requested);
        // 与签发相同的tenant -> package顺序；纯资料修改不扫描租户和角色。
        List<TenantDO> tenants=resourcesChanged?tenantMapper.selectAllByPackageForUpdate(id):Collections.emptyList();
        TenantPackageDO pack=editable(id,version,false);
        Set<Long> target=catalog.validateSelection("merchant",requested,pack.getMenuIds());
        Context context=new Context();context.pack=pack;context.target=target;context.tenants=tenants;
        PackageImpactRespVO preview=new PackageImpactRespVO();preview.setPackageId(id);preview.setExpectedVersion(version);
        List<Object> canonicalTenants=new ArrayList<>();int changed=0,added=0,removed=0;
        for(TenantDO tenant:tenants) {
            if(tenant.getAuthVersion()==null || tenant.getAuthVersion()<0 || tenant.getAuthVersion()==Long.MAX_VALUE) {
                throw new IllegalStateException("租户版本数据异常");
            }
            List<RolePlan> plans=synchronizer.plan(tenant,target,actor);context.roles.add(plans);
            int tenantChanged=0,tenantAdded=0,tenantRemoved=0;List<Object> canonicalRoles=new ArrayList<>();
            for(RolePlan plan:plans) {
                if(plan.changed()) { tenantChanged++; }tenantAdded+=plan.added();tenantRemoved+=plan.removed();
                Map<String,Object> snapshot=new TreeMap<>();snapshot.put("id",plan.getRole().getId());snapshot.put("version",plan.getRole().getAuthVersion());
                snapshot.put("before",plan.getBefore());snapshot.put("target",plan.getTarget());snapshot.put("status",plan.getRole().getStatus());canonicalRoles.add(snapshot);
            }
            PackageImpactRespVO.TenantImpact item=new PackageImpactRespVO.TenantImpact();item.setTenantId(tenant.getId());item.setCode(tenant.getCode());
            item.setExpectedVersion(tenant.getAuthVersion());item.setDeleted(tenant.getDeleted());item.setChangedRoles(tenantChanged);
            item.setAddedBindings(tenantAdded);item.setRemovedBindings(tenantRemoved);preview.getTenants().add(item);
            Map<String,Object> snapshot=new TreeMap<>();snapshot.put("id",tenant.getId());snapshot.put("version",tenant.getAuthVersion());
            snapshot.put("deleted",tenant.getDeleted());snapshot.put("adminUserId",tenant.getContactUserId());snapshot.put("adminRoleId",tenant.getAdminRoleId());
            snapshot.put("roles",canonicalRoles);canonicalTenants.add(snapshot);
            changed+=tenantChanged;added+=tenantAdded;removed+=tenantRemoved;
        }
        preview.setAffectedTenants(tenants.size());preview.setChangedRoles(changed);preview.setAddedBindings(added);preview.setRemovedBindings(removed);
        Map<String,Object> canonical=new TreeMap<>();canonical.put("packageId",id);canonical.put("version",version);canonical.put("target",target);
        canonical.put("tenants",canonicalTenants);canonical.put("catalog",catalog.fingerprintState("merchant",target));
        preview.setImpactToken(ManagementFingerprint.sha256(canonical));context.preview=preview;return context;
    }
    private TenantPackageDO editable(Long id,Long version,boolean deleted) {
        requireVersion(id,version);TenantPackageDO row=packageMapper.selectLifecycleForUpdate(id);
        if(row==null || Boolean.TRUE.equals(row.getDeleted())!=deleted) { throw exception0(404,"套餐不存在"); }
        if(!version.equals(row.getAuthVersion())) { throw exception0(409,"套餐已变化，请重新读取"); }return row;
    }
    private void requireVersion(Long id,Long version) {
        if(id==null || id<=0 || version==null || version<0 || version==Long.MAX_VALUE) { throw exception0(400,"套餐编号或版本不合法"); }
    }
    private TenantPackageChangeDO findChange(String id) {
        return changeMapper.selectOne(new LambdaQueryWrapper<TenantPackageChangeDO>().eq(TenantPackageChangeDO::getOperationId,id));
    }
    private void requireOperationId(String value) {
        if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) { throw exception0(400,"更新套餐必须提供稳定操作编号"); }
    }
    private String requestHash(PackageSaveReqVO body) {
        Map<String,Object> value=new TreeMap<>();value.put("id",body.getId());value.put("expectedVersion",body.getExpectedVersion());
        value.put("name",body.getName());value.put("status",body.getStatus());value.put("remark",body.getRemark());value.put("menuIds",new TreeSet<>(body.getMenuIds()));
        return ManagementFingerprint.sha256(value);
    }
    private Map<String,Object> summary(PackageImpactRespVO preview) {
        Map<String,Object> value=new LinkedHashMap<>();value.put("affectedTenants",preview.getAffectedTenants());value.put("changedRoles",preview.getChangedRoles());
        value.put("addedBindings",preview.getAddedBindings());value.put("removedBindings",preview.getRemovedBindings());
        value.put("tenantIds",preview.getTenants().stream().map(PackageImpactRespVO.TenantImpact::getTenantId).collect(Collectors.toList()));return value;
    }
    private LambdaUpdateWrapper<TenantPackageDO> change(TenantPackageDO row,ManagementTransactions.Actor actor) {
        return new LambdaUpdateWrapper<TenantPackageDO>().eq(TenantPackageDO::getId,row.getId()).eq(TenantPackageDO::getAuthVersion,row.getAuthVersion())
                .set(TenantPackageDO::getAuthVersion,row.getAuthVersion()+1).set(TenantPackageDO::getUpdater,actor.getId().toString()).set(TenantPackageDO::getUpdateTime,actor.getNow());
    }
    private Long recorded(ManagementTransactions.Actor actor,TenantPackageDO row,String action,Map<String,Object> details) {
        audit.record(actor,0L,action,row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);return row.getAuthVersion()+1;
    }
    private void requireChanged(int count) { if(count!=1) { throw exception0(409,"套餐已变化，请重新读取"); } }
    private static class Context {
        private TenantPackageDO pack;private Set<Long> target;private List<TenantDO> tenants;
        private List<List<RolePlan>> roles=new ArrayList<>();private PackageImpactRespVO preview;
    }
}
