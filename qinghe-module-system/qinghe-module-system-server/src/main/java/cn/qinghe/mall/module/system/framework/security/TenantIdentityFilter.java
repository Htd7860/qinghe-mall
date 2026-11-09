package cn.qinghe.mall.module.system.framework.security;

import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import org.slf4j.MDC;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 在Security认证之后、业务处理之前核对租户归属。仅由注册配置创建。 */
public class TenantIdentityFilter extends OncePerRequestFilter {

    private final AccessDeniedHandler accessDeniedHandler;

    public TenantIdentityFilter(AccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // 只使用已认证身份；不采用请求头中的tenant/user/actor作为日志身份。
        MDC.remove("tenantId");MDC.remove("actorId");
        request.removeAttribute(TraceUtils.AUTHENTICATED_TENANT_ATTRIBUTE);
        request.removeAttribute(TraceUtils.AUTHENTICATED_ACTOR_ATTRIBUTE);
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long identityTenantId = user.getTenantId();
        if (identityTenantId == null || identityTenantId < 0) {
            throw new IllegalStateException("已认证租户身份不完整");
        }

        Long requestTenantId = TenantContextHolder.getTenantId();
        if (requestTenantId != null
                && !requestTenantId.equals(identityTenantId)) {
            accessDeniedHandler.handle(request, response,
                    new AccessDeniedException("无权访问其他租户"));
            return;
        }

        TenantContextHolder.setTenantId(identityTenantId);
        if(user.getId()!=null && user.getId()>0) {
            MDC.put("tenantId",identityTenantId.toString());MDC.put("actorId",user.getId().toString());
            request.setAttribute(TraceUtils.AUTHENTICATED_TENANT_ATTRIBUTE,identityTenantId);
            request.setAttribute(TraceUtils.AUTHENTICATED_ACTOR_ATTRIBUTE,user.getId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.setTenantId(requestTenantId);
            MDC.remove("tenantId");MDC.remove("actorId");
        }
    }
}
