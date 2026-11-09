package cn.qinghe.mall.module.system.api.permission;

import cn.qinghe.mall.framework.common.biz.system.permission.PermissionCommonApi;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.service.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 当前system进程内适配；未来跨服务传输仍需单独实现可信认证/租户传播。 */
@Service
public class PermissionCommonApiImpl implements PermissionCommonApi {

    private final PermissionService permissionService;

    public PermissionCommonApiImpl(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public CommonResult<Boolean> hasAnyPermissions(Long userId, Long tenantId, String... permissions) {
        if (userId == null || userId <= 0 || tenantId == null || tenantId < 0
                || TenantContextHolder.isIgnore()
                || !tenantId.equals(TenantContextHolder.getTenantId())
                || permissions == null || permissions.length == 0) {
            return CommonResult.success(false);
        }
        for (String permission : permissions) {
            if (!StringUtils.hasText(permission)) {
                return CommonResult.success(false);
            }
        }
        List<String> granted = permissionService.getUserPermission(userId).getPermissions();
        for (String permission : permissions) {
            if (granted.contains(permission)) {
                return CommonResult.success(true);
            }
        }
        return CommonResult.success(false);
    }
}
