package cn.qinghe.mall.module.system.controller.internal.auth;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;

/** 普通经营写入资格；不接受外部目标租户，不用于历史订单/退款履约。 */
@RestController
public class InternalTenantOperationController {
    private final TenantService tenantService;

    public InternalTenantOperationController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/internal-api/auth/tenant/operation/check")
    public CommonResult<Boolean> checkCurrentTenant(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || user.getTenantId() == null || user.getTenantId() <= 0) {
            throw new AccessDeniedException("经营入口与身份不匹配");
        }
        tenantService.validateForOperation(user.getTenantId());
        return CommonResult.success(true);
    }
}
