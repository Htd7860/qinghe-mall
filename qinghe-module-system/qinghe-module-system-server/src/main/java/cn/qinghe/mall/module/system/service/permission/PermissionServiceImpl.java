package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.UserRoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.MenuMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.UserRoleMapper;
import cn.qinghe.mall.module.system.service.permission.dto.UserPermissionDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantPackageService;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 直接读取当前关联，不提供基于角色码、账号类型或租户0的全权快捷分支。 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final TenantService tenantService;
    private final TenantPackageService tenantPackageService;
    private final MenuService menuService;
    private final RoleService roleService;

    public PermissionServiceImpl(UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                 RoleMenuMapper roleMenuMapper, MenuMapper menuMapper,
                                 TenantService tenantService, TenantPackageService tenantPackageService,
                                 MenuService menuService, RoleService roleService) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.tenantService = tenantService;
        this.tenantPackageService = tenantPackageService;
        this.menuService = menuService;
        this.roleService = roleService;
    }

    @Override
    public UserPermissionDTO getUserPermission(Long userId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("权限聚合必须处于可信租户上下文");
        }
        Set<Long> roleIds = userRoleMapper.selectListByUserId(userId).stream()
                .map(UserRoleDO::getRoleId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RoleDO> roles = roleMapper.selectListByIds(roleIds).stream()
                .filter(role -> CommonStatusEnum.isEnable(role.getStatus())).collect(Collectors.toList());
        List<String> roleNames = roles.stream().map(RoleDO::getName).distinct().collect(Collectors.toList());
        if (roles.isEmpty()) {
            return new UserPermissionDTO(roleNames, Collections.emptyList());
        }

        // 只有仍启用的角色才参与取资源，不能使用过滤前的roleIds。
        Set<Long> enabledRoleIds = roles.stream().map(RoleDO::getId).collect(Collectors.toSet());
        Set<Long> menuIds = roleMenuMapper.selectListByRoleIds(enabledRoleIds).stream()
                .map(RoleMenuDO::getMenuId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (tenantId > 0) {
            TenantDO tenant = tenantService.validateForAuthentication(tenantId);
            // 已有租户允许停用套餐，不能用validateForAssignment。
            TenantPackageDO tenantPackage = tenantPackageService.getTenantPackage(tenant.getPackageId());
            if (tenantPackage == null || tenantPackage.getMenuIds() == null) {
                throw new IllegalStateException("租户关联套餐资源不存在");
            }
            menuIds.retainAll(tenantPackage.getMenuIds());
        }

        String realm = tenantId == 0 ? "platform" : "merchant";
        List<MenuDO> menus = menuMapper.selectListByIdsAndRealm(menuIds, realm);
        Set<String> permissions = new LinkedHashSet<>();
        List<MenuDO> effectiveMenus = menuService.filterDisableMenus(menus);
        for (MenuDO menu : effectiveMenus) {
            if (StringUtils.hasText(menu.getPermission())) {
                permissions.add(menu.getPermission());
            }
        }
        return new UserPermissionDTO(roleNames, new ArrayList<>(permissions), effectiveMenus);
    }

    @Override
    public Set<Long> getRoleMenuListByRoleId(Long roleId) {
        // 先核对当前tenant内的角色；不存在/删除/跨租户统一404，停用角色仍可管理读取。
        roleService.getRole(roleId);
        Set<Long> allowedMenuIds = menuService.getConfiguredMenuList().stream()
                .map(MenuDO::getId).collect(Collectors.toSet());
        return roleMenuMapper.selectListByRoleIds(Collections.singleton(roleId)).stream()
                .map(RoleMenuDO::getMenuId)
                .filter(allowedMenuIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
