package cn.qinghe.mall.module.system.controller.internal.auth;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.service.user.UserDataScope;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashSet;
import java.util.Objects;

/** 沿用已有真实数据范围计算，不接外部user/tenant/owner参数。 */
@RestController
public class InternalDataScopeController {
    private final UserDataScope scopes;
    public InternalDataScopeController(UserDataScope scopes) { this.scopes = scopes; }
    @GetMapping("/internal-api/auth/data-scope/current")
    public CommonResult<UserDataScopeRespDTO> current(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (actor == null || !UserTypeEnum.ADMIN.getValue().equals(actor.getUserType())
                || actor.getTenantId() == null || actor.getTenantId() <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())) {
            throw new AccessDeniedException("数据范围入口与身份不匹配");
        }
        UserDataScope.Scope scope = scopes.current();
        UserDataScopeRespDTO result = new UserDataScopeRespDTO();
        result.setTenantId(actor.getTenantId()); result.setUserId(scope.getUserId());
        result.setCurrentDeptId(scope.getCurrentDeptId()); result.setAll(scope.isAll()); result.setSelf(scope.isSelf());
        result.setDeptIds(new LinkedHashSet<>(scope.getDeptIds()));
        return CommonResult.success(result);
    }
}
