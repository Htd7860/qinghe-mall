package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.MenuMapper;
import cn.qinghe.mall.module.system.service.tenant.TenantPackageService;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 全局目录按可信端/套餐裁剪，实体TenantIgnore不代表接口免鉴权。 */
@Service
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final TenantService tenantService;
    private final TenantPackageService tenantPackageService;

    public MenuServiceImpl(MenuMapper menuMapper, TenantService tenantService,
                           TenantPackageService tenantPackageService) {
        this.menuMapper = menuMapper;
        this.tenantService = tenantService;
        this.tenantPackageService = tenantPackageService;
    }

    @Override
    public List<MenuDO> getAssignableMenuList() {
        return filterDisableMenus(getConfiguredMenuList());
    }

    @Override
    public List<MenuDO> getConfiguredMenuList() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("授权资源查询必须处于可信租户上下文");
        }
        List<MenuDO> menus;
        if (tenantId == 0) {
            menus = menuMapper.selectListByRealm("platform");
        } else {
            TenantDO tenant = tenantService.validateForOperation(tenantId);
            // 已有租户可继续使用停用套餐，不调用validateForAssignment。
            TenantPackageDO tenantPackage = tenantPackageService.getTenantPackage(tenant.getPackageId());
            if (tenantPackage == null || tenantPackage.getMenuIds() == null) {
                throw new IllegalStateException("租户关联套餐资源不存在");
            }
            menus = menuMapper.selectListByIdsAndRealm(tenantPackage.getMenuIds(), "merchant");
        }
        return menus;
    }

    @Override
    public List<MenuDO> filterDisableMenus(List<MenuDO> menus) {
        Map<Long, MenuDO> menuMap = new HashMap<>();
        menus.forEach(menu -> menuMap.put(menu.getId(), menu));
        List<MenuDO> enabledMenus = new ArrayList<>();
        for (MenuDO menu : menus) {
            if (hasEnabledPathToRoot(menu, menuMap)) {
                enabledMenus.add(menu);
            }
        }
        return enabledMenus;
    }

    /** 原权限聚合中的同一判断：缺父/停用/环均排除，保持输入顺序。 */
    private boolean hasEnabledPathToRoot(MenuDO menu, Map<Long, MenuDO> menuMap) {
        Set<Long> visited = new HashSet<>();
        MenuDO current = menu;
        while (current != null) {
            if (!visited.add(current.getId()) || !CommonStatusEnum.isEnable(current.getStatus())) {
                return false;
            }
            if (MenuDO.ID_ROOT.equals(current.getParentId())) {
                return true;
            }
            current = menuMap.get(current.getParentId());
        }
        return false;
    }
}
