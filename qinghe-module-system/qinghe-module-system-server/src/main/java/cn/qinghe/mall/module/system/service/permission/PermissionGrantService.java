package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.module.system.controller.admin.permission.vo.permission.PermissionAssignRoleMenuReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleMenuAuditDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMenuAuditMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.qinghe.mall.module.system.service.management.ManagementTransactions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 合法范围复核、差集写入、角色版本和成功审计使用同一个本地事务。 */
@Service
@RequiredArgsConstructor
public class PermissionGrantService {
    private final ManagementTransactions transactions;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleMenuAuditMapper auditMapper;
    private final MenuService menuService;
    private final cn.qinghe.mall.module.system.service.management.ManagementMenuCatalog catalog;

    public Long assignRoleMenu(PermissionAssignRoleMenuReqVO request) {
        if (request == null || request.getRoleId() == null || request.getRoleId() <= 0
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || request.getExpectedVersion() == Long.MAX_VALUE || request.getMenuIds() == null
                || request.getMenuIds().size() > 10000
                || request.getMenuIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw exception0(400, "角色授权参数不合法");
        }
        Long roleId = request.getRoleId();
        Long expectedVersion = request.getExpectedVersion();
        Set<Long> target = new TreeSet<>(request.getMenuIds());
        return transactions.execute("system:permission:assign-role-menu", actor -> {
            RoleDO role = roleMapper.selectByIdAndTenantForUpdate(roleId);
            if (role == null) {
                throw exception0(404, "角色不存在");
            }
            if (!Objects.equals(role.getAuthVersion(), expectedVersion)) {
                throw exception0(409, "角色授权已变化，请重新读取后再提交");
            }
            if (Integer.valueOf(1).equals(role.getType())) {
                throw exception0(403, "内置管理员角色由平台开通及套餐流程维护");
            }
            Map<Long, RoleMenuDO> original = new LinkedHashMap<>();
            Set<Long> before = new LinkedHashSet<>();
            for (RoleMenuDO row : roleMenuMapper.selectAllByRoleIdForUpdate(roleId)) {
                if (original.put(row.getMenuId(), row) != null) {
                    throw new IllegalStateException("角色资源关联不唯一");
                }
                if (!Boolean.TRUE.equals(row.getDeleted())) {
                    before.add(row.getMenuId());
                }
            }
            Set<Long> allowed = menuService.getConfiguredMenuList().stream()
                    .map(MenuDO::getId).collect(Collectors.toSet());
            if (!allowed.containsAll(target)) {
                throw exception0(403, "所选资源不在当前端或套餐范围内");
            }
            // 旧停用绑定允许原样保留；新增必须自身及父链启用，避免回显遗漏造成误撤销。
            catalog.validateSelection(actor.getTenantId() == 0 ? "platform" : "merchant", target, before);
            if (before.equals(target)) {
                return role.getAuthVersion();
            }
            Set<Long> removed = new LinkedHashSet<>(before);
            removed.removeAll(target);
            if (roleMenuMapper.revokeByRoleIdAndMenuIds(roleId, removed, actor.getId(), actor.getNow())
                    != removed.size()) {
                throw new IllegalStateException("角色资源撤销结果不一致");
            }
            for (Long menuId : target) {
                RoleMenuDO existing = original.get(menuId);
                if (existing == null) {
                    RoleMenuDO row = new RoleMenuDO();
                    row.setTenantId(actor.getTenantId());
                    row.setRoleId(roleId);
                    row.setMenuId(menuId);
                    row.setCreator(actor.getId().toString());
                    row.setUpdater(actor.getId().toString());
                    row.setCreateTime(actor.getNow());
                    row.setUpdateTime(actor.getNow());
                    row.setDeleted(false);
                    if (roleMenuMapper.insert(row) != 1) {
                        throw new IllegalStateException("角色资源新增结果异常");
                    }
                } else if (Boolean.TRUE.equals(existing.getDeleted())
                        && roleMenuMapper.restoreByIdAndRole(existing.getId(), roleId,
                                actor.getId(), actor.getNow()) != 1) {
                    throw new IllegalStateException("角色资源恢复结果异常");
                }
            }
            if (roleMapper.updateAuthVersion(roleId, expectedVersion, actor.getId(), actor.getNow()) != 1) {
                throw exception0(409, "角色授权已变化，请重新读取后再提交");
            }
            RoleMenuAuditDO audit = new RoleMenuAuditDO();
            audit.setTenantId(actor.getTenantId());
            audit.setRoleId(roleId);
            audit.setActorUserId(actor.getId());
            audit.setFromVersion(expectedVersion);
            audit.setToVersion(expectedVersion + 1);
            audit.setBeforeMenuIds(before);
            audit.setAfterMenuIds(new LinkedHashSet<>(target));
            audit.setTraceId(ManagementTransactions.traceId());
            audit.setCreateTime(actor.getNow());
            if (auditMapper.insertForCurrentTenant(audit) != 1) {
                throw new IllegalStateException("角色授权审计保存异常");
            }
            return expectedVersion + 1;
        });
    }
}
