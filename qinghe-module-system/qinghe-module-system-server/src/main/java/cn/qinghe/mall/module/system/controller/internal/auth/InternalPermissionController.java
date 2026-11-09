package cn.qinghe.mall.module.system.controller.internal.auth;

import cn.qinghe.mall.framework.common.biz.system.permission.PermissionCommonApi;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.PermissionCheckReqDTO;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 受Bearer安全链与租户身份桥接保护；只查当前用户，网关不向外路由此入口。 */
@RestController
public class InternalPermissionController {
    private final PermissionCommonApi permissionApi;

    public InternalPermissionController(PermissionCommonApi permissionApi) {
        this.permissionApi = permissionApi;
    }

    @PostMapping("/internal-api/auth/permission/check")
    public CommonResult<Boolean> checkCurrentPermission(@Valid @RequestBody PermissionCheckReqDTO body,
                                                       HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            throw exception0(401, "请先登录");
        }
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType())) {
            return CommonResult.success(false);
        }
        return permissionApi.hasAnyPermissions(user.getId(), user.getTenantId(),
                body.getPermissions().toArray(new String[0]));
    }
}
