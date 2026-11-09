package cn.qinghe.mall.module.system.service.management;

import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedIdReqVO;
import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedStatusReqVO;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.UserRoleMapper;
import cn.qinghe.mall.module.system.dal.dataobject.permission.UserRoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 写职责独立于资格读取RoleService，避免事务入口与权限聚合循环依赖。 */
@Service
@RequiredArgsConstructor
public class RoleManagementService {
    private final ManagementTransactions transactions;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final AdminUserMapper userMapper;
    private final ManagementAudit audit;

    public Long create(RoleSaveReqVO request) {
        return transactions.execute("system:role:create", actor -> {
            if (request.getId() != null || request.getExpectedVersion() != null) {
                throw exception0(400, "创建角色不能指定编号或版本");
            }
            if ("tenant_admin".equals(request.getCode()) || "platform_admin".equals(request.getCode())
                    || "super_admin".equals(request.getCode())) {
                throw exception0(403, "该角色标识由可信开通流程保留");
            }
            RoleDO role = new RoleDO();
            role.setTenantId(actor.getTenantId());
            role.setName(request.getName());
            role.setCode(request.getCode());
            role.setSort(request.getSort());
            role.setStatus(request.getStatus());
            role.setRemark(request.getRemark());
            role.setType(2);
            role.setDataScope(5);
            role.setDataScopeDeptIds(Collections.emptySet());
            role.setAuthVersion(0L);
            role.setCreator(actor.getId().toString());
            role.setUpdater(actor.getId().toString());
            role.setCreateTime(actor.getNow());
            role.setUpdateTime(actor.getNow());
            role.setDeleted(false);
            if (roleMapper.insert(role) != 1 || role.getId() == null) {
                throw new IllegalStateException("角色创建结果异常");
            }
            audit.record(actor, actor.getTenantId(), "ROLE.CREATE", role.getId(), -1L, 0L,
                    Collections.singletonMap("code", role.getCode()));
            return role.getId();
        });
    }

    public Long update(RoleSaveReqVO request) {
        return transactions.execute("system:role:update", actor -> {
            RoleDO role = editable(request.getId(), request.getExpectedVersion(), false);
            if (!role.getCode().equals(request.getCode())) {
                throw exception0(400, "角色标识创建后不可修改");
            }
            if (Objects.equals(role.getName(), request.getName()) && Objects.equals(role.getSort(), request.getSort())
                    && Objects.equals(role.getStatus(), request.getStatus())
                    && Objects.equals(role.getRemark(), request.getRemark())) {
                return role.getAuthVersion();
            }
            int changed = roleMapper.update(null, change(role, actor)
                    .set(RoleDO::getName, request.getName()).set(RoleDO::getSort, request.getSort())
                    .set(RoleDO::getStatus, request.getStatus()).set(RoleDO::getRemark, request.getRemark()));
            requireChanged(changed);
            audit.record(actor, actor.getTenantId(), "ROLE.UPDATE", role.getId(), role.getAuthVersion(),
                    role.getAuthVersion() + 1, Collections.singletonMap("status", request.getStatus()));
            return role.getAuthVersion() + 1;
        });
    }

    public Long status(VersionedStatusReqVO request) {
        return transactions.execute("system:role:update", actor -> {
            RoleDO role = editable(request.getId(), request.getExpectedVersion(), false);
            if (Objects.equals(role.getStatus(), request.getStatus())) {
                return role.getAuthVersion();
            }
            requireChanged(roleMapper.update(null, change(role, actor).set(RoleDO::getStatus, request.getStatus())));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("beforeStatus", role.getStatus());
            details.put("afterStatus", request.getStatus());
            audit.record(actor, actor.getTenantId(), "ROLE.STATUS", role.getId(), role.getAuthVersion(),
                    role.getAuthVersion() + 1, details);
            return role.getAuthVersion() + 1;
        });
    }

    public Long delete(VersionedIdReqVO request) {
        return transactions.execute("system:role:delete", actor -> {
            RoleDO role = editable(request.getId(), request.getExpectedVersion(), false);
            revokeBindings(role, actor);
            requireChanged(roleMapper.update(null, change(role, actor)
                    .set(RoleDO::getDeleted, true).set(RoleDO::getStatus, 1)));
            audit.record(actor, actor.getTenantId(), "ROLE.DELETE", role.getId(), role.getAuthVersion(),
                    role.getAuthVersion() + 1, Collections.singletonMap("bindingsRevoked", true));
            return role.getAuthVersion() + 1;
        });
    }

    public Long restore(VersionedIdReqVO request) {
        return transactions.execute("system:role:restore", actor -> {
            RoleDO role = editable(request.getId(), request.getExpectedVersion(), true);
            revokeBindings(role, actor);
            requireChanged(roleMapper.restoreLifecycleSql(actor.getTenantId(), role.getId(), role.getAuthVersion(),
                    actor.getId().toString(), actor.getNow()));
            audit.record(actor, actor.getTenantId(), "ROLE.RESTORE", role.getId(), role.getAuthVersion(),
                    role.getAuthVersion() + 1, Collections.singletonMap("disabledWithoutBindings", true));
            return role.getAuthVersion() + 1;
        });
    }

    private RoleDO editable(Long id, Long expectedVersion, boolean deleted) {
        if (id == null || id <= 0 || expectedVersion == null || expectedVersion < 0
                || expectedVersion == Long.MAX_VALUE) {
            throw exception0(400, "角色编号或版本不合法");
        }
        RoleDO role = roleMapper.selectLifecycleForUpdate(id);
        if (role == null || Boolean.TRUE.equals(role.getDeleted()) != deleted) {
            throw exception0(404, deleted ? "待恢复角色不存在" : "角色不存在");
        }
        if (!Objects.equals(role.getAuthVersion(), expectedVersion)) {
            throw exception0(409, "角色已变化，请重新读取");
        }
        if (Integer.valueOf(1).equals(role.getType())) {
            throw exception0(403, "内置管理员角色不能执行此操作");
        }
        return role;
    }

    private LambdaUpdateWrapper<RoleDO> change(RoleDO role, ManagementTransactions.Actor actor) {
        return new LambdaUpdateWrapper<RoleDO>().eq(RoleDO::getTenantId, actor.getTenantId())
                .eq(RoleDO::getId, role.getId()).eq(RoleDO::getAuthVersion, role.getAuthVersion())
                .set(RoleDO::getAuthVersion, role.getAuthVersion() + 1)
                .set(RoleDO::getUpdater, actor.getId().toString()).set(RoleDO::getUpdateTime, actor.getNow());
    }

    private void revokeBindings(RoleDO role, ManagementTransactions.Actor actor) {
        Set<Long> active = roleMenuMapper.selectAllByRoleIdForUpdate(role.getId()).stream()
                .filter(row -> !Boolean.TRUE.equals(row.getDeleted())).map(RoleMenuDO::getMenuId)
                .collect(Collectors.toSet());
        if (roleMenuMapper.revokeByRoleIdAndMenuIds(role.getId(), active, actor.getId(), actor.getNow()) != active.size()) {
            throw new IllegalStateException("角色关联撤销结果不一致");
        }
        Set<Long> affectedUsers = userRoleMapper.selectListByRoleId(role.getId()).stream()
                .map(UserRoleDO::getUserId).collect(Collectors.toSet());
        if (!affectedUsers.isEmpty()) {
            long valid = userMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>()
                    .eq(AdminUserDO::getTenantId, actor.getTenantId()).in(AdminUserDO::getId, affectedUsers)
                    .lt(AdminUserDO::getAuthVersion, Long.MAX_VALUE));
            if (valid != affectedUsers.size()) { throw new IllegalStateException("关联人员或其版本数据异常"); }
            int changed = userMapper.update(null, new LambdaUpdateWrapper<AdminUserDO>()
                    .eq(AdminUserDO::getTenantId, actor.getTenantId()).in(AdminUserDO::getId, affectedUsers)
                    .lt(AdminUserDO::getAuthVersion, Long.MAX_VALUE).setSql("auth_version = auth_version + 1")
                    .set(AdminUserDO::getUpdater, actor.getId().toString()).set(AdminUserDO::getUpdateTime, actor.getNow()));
            if (changed != affectedUsers.size()) { throw new IllegalStateException("人员授权版本更新异常"); }
        }
        if (userRoleMapper.revokeByRoleId(role.getId(), actor.getId(), actor.getNow()) != affectedUsers.size()) {
            throw new IllegalStateException("角色人员关联撤销结果异常");
        }
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw exception0(409, "角色已变化，请重新读取");
        }
    }
}
