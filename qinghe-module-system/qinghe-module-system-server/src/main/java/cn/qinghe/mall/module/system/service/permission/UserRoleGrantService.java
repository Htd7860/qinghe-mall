package cn.qinghe.mall.module.system.service.permission;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.permission.PermissionAssignUserRoleReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.*;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.service.management.*;
import cn.qinghe.mall.module.system.service.user.UserDataScope;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
@Service
@RequiredArgsConstructor
public class UserRoleGrantService {
    private final ManagementTransactions transactions;
    private final AdminUserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final ManagedAdministrators administrators;
    private final ManagementAudit audit;
    private final UserDataScope dataScope;
    private final TenantService tenantService;

    public Set<Long> getRoleIds(Long userId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId > 0) { tenantService.validateForOperation(tenantId); }
        AdminUserDO user = userMapper.selectByIdAndTenant(userId, tenantId);
        dataScope.current().requireVisible(user);
        return userRoleMapper.selectListByUserId(userId).stream().map(UserRoleDO::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Long assign(PermissionAssignUserRoleReqVO request) {
        Set<Long> target = new TreeSet<>(request.getRoleIds());
        return transactions.execute("system:permission:assign-user-role", actor -> {
            AdminUserDO user = userMapper.selectLifecycleSql(actor.getTenantId(), request.getUserId());
            if (user == null || Boolean.TRUE.equals(user.getDeleted())) { throw exception0(404, "人员不存在"); }
            dataScope.current().requireVisible(user);
            administrators.requireOrdinaryUser(actor.getTenantId(), user.getId());
            if (!Objects.equals(user.getAuthVersion(), request.getExpectedVersion())) {
                throw exception0(409, "人员资料或授权已变化，请重新读取");
            }
            List<RoleDO> roles = roleMapper.selectListByIds(target);
            if (roles.size() != target.size() || roles.stream().anyMatch(role -> Integer.valueOf(1).equals(role.getType()))) {
                throw exception0(400, "只能分配本租户未删除的自定义角色");
            }
            Map<Long, UserRoleDO> original = new LinkedHashMap<>();
            Set<Long> before = new LinkedHashSet<>();
            for (UserRoleDO row : userRoleMapper.selectAllForUpdateSql(actor.getTenantId(), user.getId())) {
                if (original.put(row.getRoleId(), row) != null) { throw new IllegalStateException("人员角色关联不唯一"); }
                if (!Boolean.TRUE.equals(row.getDeleted())) { before.add(row.getRoleId()); }
            }
            if (roles.stream().anyMatch(role -> !before.contains(role.getId())
                    && !Integer.valueOf(0).equals(role.getStatus()))) {
                throw exception0(400, "不能新增已停用角色，原有停用绑定可保留或撤销");
            }
            if (before.equals(target)) { return user.getAuthVersion(); }
            Set<Long> removed = new LinkedHashSet<>(before); removed.removeAll(target);
            if (!removed.isEmpty()) {
                int changed = userRoleMapper.update(null, new LambdaUpdateWrapper<UserRoleDO>()
                        .eq(UserRoleDO::getTenantId, actor.getTenantId()).eq(UserRoleDO::getUserId, user.getId())
                        .in(UserRoleDO::getRoleId, removed).set(UserRoleDO::getDeleted, true)
                        .set(UserRoleDO::getUpdater, actor.getId().toString()).set(UserRoleDO::getUpdateTime, actor.getNow()));
                if (changed != removed.size()) { throw new IllegalStateException("人员角色撤销结果不一致"); }
            }
            for (Long roleId : target) {
                UserRoleDO old = original.get(roleId);
                if (old == null) {
                    UserRoleDO row = new UserRoleDO();
                    row.setTenantId(actor.getTenantId()); row.setUserId(user.getId()); row.setRoleId(roleId);
                    row.setCreator(actor.getId().toString()); row.setUpdater(actor.getId().toString());
                    row.setCreateTime(actor.getNow()); row.setUpdateTime(actor.getNow()); row.setDeleted(false);
                    if (userRoleMapper.insert(row) != 1) { throw new IllegalStateException("人员角色保存异常"); }
                } else if (Boolean.TRUE.equals(old.getDeleted()) && userRoleMapper.restoreSql(actor.getTenantId(),
                        old.getId(), user.getId(), roleId, actor.getId().toString(), actor.getNow()) != 1) {
                    throw new IllegalStateException("人员角色重授异常");
                }
            }
            if (userMapper.update(null, new LambdaUpdateWrapper<AdminUserDO>()
                    .eq(AdminUserDO::getTenantId, actor.getTenantId()).eq(AdminUserDO::getId, user.getId())
                    .eq(AdminUserDO::getAuthVersion, user.getAuthVersion())
                    .set(AdminUserDO::getAuthVersion, user.getAuthVersion() + 1)
                    .set(AdminUserDO::getUpdater, actor.getId().toString()).set(AdminUserDO::getUpdateTime, actor.getNow())) != 1) {
                throw exception0(409, "人员授权已变化，请重新读取");
            }
            Map<String,Object> details = new LinkedHashMap<>();
            details.put("beforeRoleIds", before); details.put("afterRoleIds", target);
            audit.record(actor, actor.getTenantId(), "USER.ASSIGN_ROLES", user.getId(), user.getAuthVersion(),
                    user.getAuthVersion()+1, details);
            return user.getAuthVersion()+1;
        });
    }
}
