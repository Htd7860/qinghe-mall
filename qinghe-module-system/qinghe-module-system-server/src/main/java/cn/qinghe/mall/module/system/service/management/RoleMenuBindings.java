package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.permission.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 套餐/开通流程共用的绑定差集写入，不提供HTTP，也不自行放宽目标范围。 */
@Component
@RequiredArgsConstructor
public class RoleMenuBindings {
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final ManagementAudit audit;
    public boolean replace(RoleDO role,Set<Long> target,ManagementTransactions.Actor actor) {
        MybatisUtils.requireWriteTransaction();
        if(TenantContextHolder.isIgnore() || !Objects.equals(TenantContextHolder.getTenantId(),role.getTenantId())) {
            throw new IllegalStateException("资源绑定的可信归属不一致");
        }
        Map<Long,RoleMenuDO> original=new LinkedHashMap<>();Set<Long> before=new TreeSet<>();
        for(RoleMenuDO row:roleMenuMapper.selectAllByRoleIdForUpdate(role.getId())) {
            if(original.put(row.getMenuId(),row)!=null) { throw new IllegalStateException("角色资源关联不唯一"); }
            if(!Boolean.TRUE.equals(row.getDeleted())) { before.add(row.getMenuId()); }
        }
        if(before.equals(target)) { return false; }
        if(role.getAuthVersion()==null || role.getAuthVersion()<0 || role.getAuthVersion()==Long.MAX_VALUE) {
            throw new IllegalStateException("角色版本数据异常");
        }
        Set<Long> removed=new TreeSet<>(before);removed.removeAll(target);
        if(roleMenuMapper.revokeByRoleIdAndMenuIds(role.getId(),removed,actor.getId(),actor.getNow())!=removed.size()) {
            throw new IllegalStateException("套餐角色资源撤销结果异常");
        }
        for(Long id:target) {
            RoleMenuDO old=original.get(id);
            if(old==null) {
                RoleMenuDO row=new RoleMenuDO();row.setTenantId(role.getTenantId());row.setRoleId(role.getId());row.setMenuId(id);
                row.setCreator(actor.getId().toString());row.setUpdater(actor.getId().toString());
                row.setCreateTime(actor.getNow());row.setUpdateTime(actor.getNow());row.setDeleted(false);
                if(roleMenuMapper.insert(row)!=1) { throw new IllegalStateException("套餐角色资源保存异常"); }
            } else if(Boolean.TRUE.equals(old.getDeleted()) && roleMenuMapper.restoreByIdAndRole(old.getId(),role.getId(),actor.getId(),actor.getNow())!=1) {
                throw new IllegalStateException("套餐管理员资源重授异常");
            }
        }
        if(roleMapper.updateAuthVersion(role.getId(),role.getAuthVersion(),actor.getId(),actor.getNow())!=1) {
            throw exception0(409,"角色资源已变化，请重新预览");
        }
        Map<String,Object> details=new LinkedHashMap<>();details.put("beforeMenuIds",before);details.put("afterMenuIds",new TreeSet<>(target));
        audit.record(actor,role.getTenantId(),"ROLE.PACKAGE_RESOURCES",role.getId(),role.getAuthVersion(),role.getAuthVersion()+1,details);
        return true;
    }
}
