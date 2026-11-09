package cn.qinghe.mall.module.system.controller.admin.permission;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.*;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.permission.*;
import cn.qinghe.mall.module.system.service.permission.UserRoleGrantService;
import cn.qinghe.mall.module.system.service.permission.RoleDataScopeGrantService;
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping({"/admin-api/system/permission", "/platform-api/system/permission"})
public class UserRoleGrantController {
    private final UserRoleGrantService userRoles;
    private final RoleDataScopeGrantService roleDataScope;
    @GetMapping("/list-user-roles")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')")
    public CommonResult<Set<Long>> getRoles(@RequestParam("userId") @Min(1) Long userId,
            HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(userRoles.getRoleIds(userId));
    }
    @PostMapping("/assign-user-role")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')")
    public CommonResult<Long> assignRoles(@Valid @RequestBody PermissionAssignUserRoleReqVO body,
            HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(userRoles.assign(body));
    }
    @PostMapping("/assign-role-data-scope")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-data-scope')")
    public CommonResult<Long> assignDataScope(@Valid @RequestBody PermissionAssignRoleDataScopeReqVO body,
            HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(roleDataScope.assign(body));
    }
    private void prepare(HttpServletRequest request, HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
