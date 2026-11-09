package cn.qinghe.mall.module.system.controller.admin.permission;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.service.permission.PermissionService;
import cn.qinghe.mall.module.system.service.permission.PermissionGrantService;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.permission.PermissionAssignRoleMenuReqVO;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Min;
import javax.validation.Valid;
import java.util.Set;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;

/** 授权回显与受版本保护的资源分配，两端分别核对身份。 */
@RestController
@Validated
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionGrantService grantService;

    public PermissionController(PermissionService permissionService, PermissionGrantService grantService) {
        this.permissionService = permissionService;
        this.grantService = grantService;
    }

    @PostMapping("/admin-api/system/permission/assign-role-menu")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<Long> assignMerchantRoleMenu(
            @Valid @RequestBody PermissionAssignRoleMenuReqVO request, HttpServletResponse response) {
        checkRealm(false);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return CommonResult.success(grantService.assignRoleMenu(request));
    }

    @PostMapping("/platform-api/system/permission/assign-role-menu")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<Long> assignPlatformRoleMenu(
            @Valid @RequestBody PermissionAssignRoleMenuReqVO request, HttpServletResponse response) {
        checkRealm(true);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return CommonResult.success(grantService.assignRoleMenu(request));
    }

    @GetMapping("/admin-api/system/permission/list-role-menus")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<Set<Long>> getMerchantRoleMenuList(
            @RequestParam("roleId") @Min(1) Long roleId, HttpServletResponse response) {
        checkRealm(false);
        return roleMenuList(roleId, response);
    }

    @GetMapping("/platform-api/system/permission/list-role-menus")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<Set<Long>> getPlatformRoleMenuList(
            @RequestParam("roleId") @Min(1) Long roleId, HttpServletResponse response) {
        checkRealm(true);
        return roleMenuList(roleId, response);
    }

    private CommonResult<Set<Long>> roleMenuList(Long roleId, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return CommonResult.success(permissionService.getRoleMenuListByRoleId(roleId));
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
}
