package cn.qinghe.mall.framework.security.core.service;

import cn.qinghe.mall.framework.common.biz.system.permission.PermissionCommonApi;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.util.StringUtils;

/** Servlet方法鉴权入口，不缓存授权结果、不提供跨租户免检分支。 */
public class SecurityFrameworkServiceImpl implements SecurityFrameworkService {

    private final PermissionCommonApi permissionApi;

    public SecurityFrameworkServiceImpl(PermissionCommonApi permissionApi) {
        this.permissionApi = permissionApi;
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasAnyPermissions(permission);
    }

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        for (String permission : permissions) {
            if (!StringUtils.hasText(permission)) {
                return false;
            }
        }
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || user.getId() == null || user.getId() <= 0
                || user.getTenantId() == null || user.getTenantId() < 0) {
            return false;
        }
        // 不接收前端传来的userId/tenantId；来自当前已认证的LoginUser。
        Boolean allowed = permissionApi.hasAnyPermissions(
                user.getId(), user.getTenantId(), permissions).getCheckedData();
        if (allowed == null) {
            throw new IllegalStateException("权限检查结果不能为空");
        }
        return allowed;
    }
}
