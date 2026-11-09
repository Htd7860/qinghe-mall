package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.admin.dept.vo.*;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.mysql.dept.DeptMapper;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
@Service
@RequiredArgsConstructor
public class DeptManagementService {
    private final ManagementTransactions transactions;
    private final DeptMapper deptMapper;
    private final AdminUserMapper userMapper;
    private final RoleMapper roleMapper;
    private final TenantService tenantService;
    private final ManagementAudit audit;

    public List<DeptRespVO> list(boolean deleted) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) { throw exception0(403, "部门查询需要可信租户"); }
        if (tenantId > 0) { tenantService.validateForOperation(tenantId); }
        return deptMapper.selectLifecycleListSql(tenantId, deleted).stream().map(DeptRespVO::from).collect(Collectors.toList());
    }
    public Long create(DeptSaveReqVO body) {
        return transactions.execute("system:dept:create", actor -> {
            if (body.getId()!=null || body.getExpectedVersion()!=null) { throw exception0(400, "创建不能指定编号或版本"); }
            validateParent(null, body.getParentId());
            DeptDO row = new DeptDO(); row.setTenantId(actor.getTenantId()); row.setName(body.getName());
            row.setParentId(body.getParentId()); row.setSort(body.getSort()); row.setStatus(body.getStatus()); row.setAuthVersion(0L);
            row.setCreator(actor.getId().toString()); row.setUpdater(actor.getId().toString());
            row.setCreateTime(actor.getNow()); row.setUpdateTime(actor.getNow()); row.setDeleted(false);
            if (deptMapper.insert(row)!=1 || row.getId()==null) { throw new IllegalStateException("部门创建结果异常"); }
            audit.record(actor, actor.getTenantId(), "DEPT.CREATE", row.getId(), -1L, 0L, Collections.singletonMap("parentId", row.getParentId()));
            return row.getId();
        });
    }
    public Long update(DeptSaveReqVO body) {
        return transactions.execute("system:dept:update", actor -> {
            DeptDO row = editable(body.getId(), body.getExpectedVersion(), false, actor);
            validateParent(row.getId(), body.getParentId());
            requireChanged(deptMapper.update(null, change(row, actor).set(DeptDO::getName, body.getName())
                    .set(DeptDO::getParentId, body.getParentId()).set(DeptDO::getSort, body.getSort()).set(DeptDO::getStatus, body.getStatus())));
            return recorded(actor, row, "DEPT.UPDATE", Collections.singletonMap("parentId", body.getParentId()));
        });
    }
    public Long status(VersionedStatusReqVO body) {
        return transactions.execute("system:dept:update", actor -> {
            DeptDO row=editable(body.getId(),body.getExpectedVersion(),false,actor);
            if (Objects.equals(row.getStatus(),body.getStatus())) { return row.getAuthVersion(); }
            if (Integer.valueOf(0).equals(body.getStatus())) { validateParent(row.getId(),row.getParentId()); }
            requireChanged(deptMapper.update(null,change(row,actor).set(DeptDO::getStatus,body.getStatus())));
            return recorded(actor,row,"DEPT.STATUS",Collections.singletonMap("status",body.getStatus()));
        });
    }
    public Long delete(VersionedIdReqVO body) {
        return transactions.execute("system:dept:delete", actor -> {
            DeptDO row=editable(body.getId(),body.getExpectedVersion(),false,actor);
            if (deptMapper.selectTenantTree().stream().anyMatch(d -> row.getId().equals(d.getParentId()))
                    || userMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>()
                            .eq(AdminUserDO::getTenantId,actor.getTenantId()).eq(AdminUserDO::getDeptId,row.getId()))>0
                    || roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getTenantId,actor.getTenantId()))
                            .stream().anyMatch(r -> r.getDataScopeDeptIds()!=null && r.getDataScopeDeptIds().contains(row.getId()))) {
                throw exception0(409,"部门仍被下级部门、员工或角色范围引用，请先调整");
            }
            requireChanged(deptMapper.update(null,change(row,actor).set(DeptDO::getDeleted,true).set(DeptDO::getStatus,1)));
            return recorded(actor,row,"DEPT.DELETE",Collections.singletonMap("referencesChecked",true));
        });
    }
    public Long restore(VersionedIdReqVO body) {
        return transactions.execute("system:dept:restore", actor -> {
            DeptDO row=editable(body.getId(),body.getExpectedVersion(),true,actor);
            validateParent(row.getId(),row.getParentId());
            requireChanged(deptMapper.restoreSql(actor.getTenantId(),row.getId(),row.getAuthVersion(),actor.getId().toString(),actor.getNow()));
            return recorded(actor,row,"DEPT.RESTORE",Collections.singletonMap("disabled",true));
        });
    }
    private void validateParent(Long id, Long parentId) {
        Map<Long,DeptDO> tree=deptMapper.selectTenantTree().stream().collect(Collectors.toMap(DeptDO::getId,d->d));
        Set<Long> visited=new HashSet<>(); Long cursor=parentId;
        while (cursor!=null && cursor>0 && visited.add(cursor)) {
            if (cursor.equals(id)) { throw exception0(400,"不能将部门移动到自己或自己的下级"); }
            DeptDO parent=tree.get(cursor);
            if (parent==null || !Integer.valueOf(0).equals(parent.getStatus())) { throw exception0(400,"父部门不存在或已停用"); }
            cursor=parent.getParentId();
        }
        if (!Long.valueOf(0).equals(cursor)) { throw exception0(400,"部门父链不完整或循环"); }
    }
    private DeptDO editable(Long id,Long version,boolean deleted,ManagementTransactions.Actor actor) {
        if (id==null || id<=0 || version==null || version<0 || version==Long.MAX_VALUE) { throw exception0(400,"部门编号或版本不合法"); }
        DeptDO row=deptMapper.selectLifecycleSql(actor.getTenantId(),id);
        if (row==null || Boolean.TRUE.equals(row.getDeleted())!=deleted) { throw exception0(404,"部门不存在"); }
        if (!version.equals(row.getAuthVersion())) { throw exception0(409,"部门已变化，请重新读取"); }
        return row;
    }
    private LambdaUpdateWrapper<DeptDO> change(DeptDO row,ManagementTransactions.Actor actor) {
        return new LambdaUpdateWrapper<DeptDO>().eq(DeptDO::getTenantId,actor.getTenantId()).eq(DeptDO::getId,row.getId())
                .eq(DeptDO::getAuthVersion,row.getAuthVersion()).set(DeptDO::getAuthVersion,row.getAuthVersion()+1)
                .set(DeptDO::getUpdater,actor.getId().toString()).set(DeptDO::getUpdateTime,actor.getNow());
    }
    private Long recorded(ManagementTransactions.Actor actor,DeptDO row,String action,Map<String,Object> details) {
        audit.record(actor,actor.getTenantId(),action,row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);
        return row.getAuthVersion()+1;
    }
    private void requireChanged(int count) { if(count!=1) { throw exception0(409,"部门已变化，请重新读取"); } }
}
