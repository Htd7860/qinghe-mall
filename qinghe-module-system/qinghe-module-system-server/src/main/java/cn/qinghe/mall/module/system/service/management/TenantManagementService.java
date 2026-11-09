package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.*;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.*;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.tenant.*;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.service.tenant.TenantPackageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

@Service
@RequiredArgsConstructor
public class TenantManagementService {
    private final ManagementTransactions transactions;
    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final AdminUserMapper userMapper;
    private final TenantPackageService packageService;
    private final ManagementMenuCatalog catalog;
    private final TenantAdminProvisioner provisioner;
    private final TenantRoleMenuSynchronizer synchronizer;
    private final ManagementAudit audit;

    public PageResult<TenantRespVO> page(TenantPageReqVO query) {
        ManagementTransactions.requirePlatformRead();
        Page<TenantDO> page=MybatisUtils.buildPage(query);
        page.setRecords(tenantMapper.selectManagementPage(page,query));
        return new PageResult<>(page.getTotal(),page.getRecords().stream().map(TenantRespVO::from).collect(Collectors.toList()));
    }
    public TenantRespVO get(Long id) {
        ManagementTransactions.requirePlatformRead();TenantDO row=tenantMapper.selectLifecycleDetail(id);
        if(row==null) { throw exception0(404,"租户不存在"); }return TenantRespVO.from(row);
    }
    public Long create(TenantSaveReqVO body) {
        return transactions.execute("system:tenant:create",actor -> {
            actor.requirePlatform();
            if(body.getId()!=null || body.getExpectedVersion()!=null) { throw exception0(400,"开通不能指定编号或版本"); }
            if(!body.getExpireTime().isAfter(LocalDateTime.now())) { throw exception0(400,"开通到期时间必须晚于当前时间"); }
            TenantPackageDO pack=lockPackage(body.getPackageId());packageService.validateForAssignment(pack.getId());
            Set<Long> menus=catalog.validateSelection("merchant",pack.getMenuIds(),pack.getMenuIds());
            TenantDO row=new TenantDO();row.setCode(body.getCode());row.setName(body.getName());
            row.setContactName(empty(body.getContactName()));row.setContactMobile(empty(body.getContactMobile()));
            row.setStatus(body.getStatus());row.setPackageId(pack.getId());row.setExpireTime(body.getExpireTime());
            row.setAccountCount(body.getAccountCount());row.setAuthVersion(0L);row.setDeleted(false);
            row.setCreator(actor.getId().toString());row.setUpdater(actor.getId().toString());
            row.setCreateTime(actor.getNow());row.setUpdateTime(actor.getNow());
            if(tenantMapper.insert(row)!=1 || row.getId()==null || row.getId()<=0) { throw new IllegalStateException("租户开通保存异常"); }
            TenantAdminProvisioner.AdminReference admin=provisioner.provision(row,body,menus,actor);
            if(tenantMapper.update(null,new LambdaUpdateWrapper<TenantDO>().eq(TenantDO::getId,row.getId())
                    .eq(TenantDO::getAuthVersion,0L).set(TenantDO::getContactUserId,admin.getUserId())
                    .set(TenantDO::getAdminRoleId,admin.getRoleId()).set(TenantDO::getUpdater,actor.getId().toString())
                    .set(TenantDO::getUpdateTime,actor.getNow()))!=1) { throw new IllegalStateException("指定管理员引用保存异常"); }
            Map<String,Object> details=new LinkedHashMap<>();details.put("packageId",pack.getId());details.put("adminUserId",admin.getUserId());
            details.put("adminRoleId",admin.getRoleId());details.put("accountCount",row.getAccountCount());
            audit.record(actor,row.getId(),"TENANT.CREATE",row.getId(),-1L,0L,details);
            return row.getId();
        });
    }
    public Long update(TenantSaveReqVO body) {
        return transactions.execute("system:tenant:update",actor -> {
            actor.requirePlatform();TenantDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(!Objects.equals(row.getCode(),body.getCode())) { throw exception0(400,"商家编码创建后不可修改"); }
            if(body.getAdminUsername()!=null || body.getAdminNickname()!=null || body.getAdminPassword()!=null) {
                throw exception0(400,"普通资料更新不能更换管理员账号或密码");
            }
            TenantPackageDO pack=lockPackage(body.getPackageId());
            boolean changePackage=!Objects.equals(row.getPackageId(),pack.getId());
            if(changePackage) { packageService.validateForAssignment(pack.getId()); }
            requireQuota(row.getId(),body.getAccountCount());
            if(changePackage) {
                Set<Long> allowed=catalog.validateSelection("merchant",pack.getMenuIds(),pack.getMenuIds());
                synchronizer.apply(row,synchronizer.plan(row,allowed,actor),actor);
            }
            requireChanged(tenantMapper.update(null,change(row,actor).set(TenantDO::getName,body.getName())
                    .set(TenantDO::getContactName,empty(body.getContactName())).set(TenantDO::getContactMobile,empty(body.getContactMobile()))
                    .set(TenantDO::getStatus,body.getStatus()).set(TenantDO::getPackageId,pack.getId())
                    .set(TenantDO::getExpireTime,body.getExpireTime()).set(TenantDO::getAccountCount,body.getAccountCount())));
            Map<String,Object> details=new LinkedHashMap<>();details.put("beforePackageId",row.getPackageId());details.put("afterPackageId",pack.getId());
            details.put("status",body.getStatus());details.put("accountCount",body.getAccountCount());details.put("expireTime",body.getExpireTime().toString());
            return recorded(actor,row,"TENANT.UPDATE",details);
        });
    }
    public Long status(VersionedStatusReqVO body) {
        return transactions.execute("system:tenant:update",actor -> {
            actor.requirePlatform();TenantDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(Objects.equals(row.getStatus(),body.getStatus())) { return row.getAuthVersion(); }
            requireChanged(tenantMapper.update(null,change(row,actor).set(TenantDO::getStatus,body.getStatus())));
            // 停用是经营限制，保留身份供历史履约；不把停用等同账号/令牌删除。
            return recorded(actor,row,"TENANT.STATUS",Collections.singletonMap("status",body.getStatus()));
        });
    }
    public Long delete(VersionedIdReqVO body) {
        return transactions.execute("system:tenant:delete",actor -> {
            actor.requirePlatform();TenantDO row=editable(body.getId(),body.getExpectedVersion(),false);
            int users=invalidateSessions(row.getId(),actor);
            requireChanged(tenantMapper.update(null,change(row,actor).set(TenantDO::getDeleted,true).set(TenantDO::getStatus,1)));
            return recorded(actor,row,"TENANT.DELETE",Collections.singletonMap("invalidatedUsers",users));
        });
    }
    public Long restore(VersionedIdReqVO body) {
        return transactions.execute("system:tenant:restore",actor -> {
            actor.requirePlatform();TenantDO row=editable(body.getId(),body.getExpectedVersion(),true);
            TenantPackageDO pack=lockPackage(row.getPackageId());
            requireQuota(row.getId(),row.getAccountCount());
            Set<Long> allowed=catalog.validateSelection("merchant",pack.getMenuIds(),pack.getMenuIds());
            synchronizer.apply(row,synchronizer.plan(row,allowed,actor),actor);
            int users=invalidateSessions(row.getId(),actor);
            requireChanged(tenantMapper.restoreLifecycle(row.getId(),row.getAuthVersion(),actor.getId().toString(),actor.getNow()));
            Map<String,Object> details=new LinkedHashMap<>();details.put("disabled",true);details.put("invalidatedUsers",users);
            return recorded(actor,row,"TENANT.RESTORE",details);
        });
    }
    private TenantDO editable(Long id,Long version,boolean deleted) {
        if(id==null || id<=0 || version==null || version<0 || version==Long.MAX_VALUE) { throw exception0(400,"租户编号或版本不合法"); }
        TenantDO row=tenantMapper.selectLifecycleForUpdate(id);
        if(row==null || Boolean.TRUE.equals(row.getDeleted())!=deleted) { throw exception0(404,"租户不存在"); }
        if(!version.equals(row.getAuthVersion())) { throw exception0(409,"租户已变化，请重新读取"); }return row;
    }
    private TenantPackageDO lockPackage(Long id) {
        TenantPackageDO pack=packageMapper.selectOne(new LambdaQueryWrapper<TenantPackageDO>().eq(TenantPackageDO::getId,id).last("FOR SHARE"));
        if(pack==null) { throw exception0(404,"租户套餐不存在"); }return pack;
    }
    private void requireQuota(Long tenantId,Integer quota) {
        long used=TenantUtils.execute(tenantId,()->userMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getTenantId,tenantId)));
        if(quota==null || quota<1 || used>quota) { throw exception0(409,"账号额度不能小于未删除账号数，禁用账号和管理员也计入"); }
    }
    private int invalidateSessions(Long tenantId,ManagementTransactions.Actor actor) {
        return TenantUtils.execute(tenantId,()-> {
            long count=userMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getTenantId,tenantId));
            long valid=userMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getTenantId,tenantId)
                    .lt(AdminUserDO::getAuthVersion,Long.MAX_VALUE).lt(AdminUserDO::getCredentialVersion,Long.MAX_VALUE));
            if(count!=valid) { throw new IllegalStateException("人员版本数据异常"); }
            if(count==0) { return 0; }
            int changed=userMapper.update(null,new LambdaUpdateWrapper<AdminUserDO>().eq(AdminUserDO::getTenantId,tenantId)
                    .lt(AdminUserDO::getAuthVersion,Long.MAX_VALUE).lt(AdminUserDO::getCredentialVersion,Long.MAX_VALUE)
                    .setSql("credential_version = credential_version + 1, auth_version = auth_version + 1")
                    .set(AdminUserDO::getUpdater,actor.getId().toString()).set(AdminUserDO::getUpdateTime,actor.getNow()));
            if(changed!=count) { throw new IllegalStateException("租户会话失效结果异常"); }return changed;
        });
    }
    private LambdaUpdateWrapper<TenantDO> change(TenantDO row,ManagementTransactions.Actor actor) {
        return new LambdaUpdateWrapper<TenantDO>().eq(TenantDO::getId,row.getId()).eq(TenantDO::getAuthVersion,row.getAuthVersion())
                .set(TenantDO::getAuthVersion,row.getAuthVersion()+1).set(TenantDO::getUpdater,actor.getId().toString()).set(TenantDO::getUpdateTime,actor.getNow());
    }
    private Long recorded(ManagementTransactions.Actor actor,TenantDO row,String action,Map<String,Object> details) {
        audit.record(actor,row.getId(),action,row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);return row.getAuthVersion()+1;
    }
    private void requireChanged(int count) { if(count!=1) { throw exception0(409,"租户已变化，请重新读取"); } }
    private String empty(String value) { return value==null?"":value; }
}
