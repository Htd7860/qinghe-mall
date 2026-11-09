package cn.qinghe.mall.module.system.controller.admin.permission;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.service.permission.MenuService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;

/** 角色授权选项，不提供全局菜单管理或匿名精简列表。 */
@RestController
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/admin-api/system/menu/simple-list")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<List<MenuSimpleRespVO>> getMerchantSimpleMenuList(HttpServletResponse response) {
        checkRealm(false);
        return simpleMenuList(response);
    }

    @GetMapping("/platform-api/system/menu/simple-list")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<List<MenuSimpleRespVO>> getPlatformSimpleMenuList(HttpServletResponse response) {
        checkRealm(true);
        return simpleMenuList(response);
    }

    private CommonResult<List<MenuSimpleRespVO>> simpleMenuList(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        List<MenuSimpleRespVO> list = menuService.getConfiguredMenuList().stream()
                .map(this::toSimpleRespVO).collect(Collectors.toList());
        return CommonResult.success(list);
    }

    private void checkRealm(boolean platform) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            throw exception(UNAUTHORIZED);
        }
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || user.getTenantId() == null
                || (platform ? user.getTenantId() != 0 : user.getTenantId() <= 0)) {
            throw exception(FORBIDDEN);
        }
    }

    private MenuSimpleRespVO toSimpleRespVO(MenuDO menu) {
        MenuSimpleRespVO result = new MenuSimpleRespVO();
        result.setId(menu.getId());
        result.setName(menu.getName());
        result.setParentId(menu.getParentId());
        result.setType(menu.getType());
        result.setSort(menu.getSort());
        result.setPermission(menu.getPermission());
        result.setStatus(menu.getStatus());
        return result;
    }
}
