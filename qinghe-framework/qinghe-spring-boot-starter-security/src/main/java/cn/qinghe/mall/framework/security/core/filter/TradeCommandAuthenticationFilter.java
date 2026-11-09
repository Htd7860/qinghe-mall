package cn.qinghe.mall.framework.security.core.filter;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 复用本项目 MemberServiceTokenAuthenticationFilter：仅精确 POST 服务入口，不构造员工/会员。 */
public class TradeCommandAuthenticationFilter extends TokenAuthenticationFilter {
    public static final String AUTHORITY = "internal:trade:order-command";
    private final Set<String> paths;
    private final byte[] expected;
    private final ObjectMapper json;

    public TradeCommandAuthenticationFilter(SecurityProperties security, OAuth2TokenCommonApi userTokens,
            AuthenticationEntryPoint entry, ObjectMapper json, String token, Collection<String> paths) {
        super(security, userTokens, entry, json);
        if (token == null || !token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}")) {
            throw new IllegalArgumentException("订单服务凭据格式不正确");
        }
        if (paths == null || paths.isEmpty() || paths.stream().anyMatch(path -> path == null
                || !path.matches("/internal-api/[a-z0-9/-]+"))) {
            throw new IllegalArgumentException("订单服务路径必须精确登记");
        }
        this.paths = Collections.unmodifiableSet(new HashSet<>(paths));
        this.expected = token.isEmpty() ? null : token.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equals(request.getMethod()) || !paths.contains(request.getServletPath())) {
            super.doFilterInternal(request, response, chain);
            return;
        }
        SecurityContextHolder.clearContext();
        if (expected == null) {
            reject(response, 503, "订单服务入口未配置");
            return;
        }
        List<String> headers = Collections.list(request.getHeaders("Authorization"));
        String authorization = headers.size() == 1 ? headers.get(0) : "";
        if (!authorization.startsWith("Bearer ") || !authorization.substring(7).matches("[A-Za-z0-9_-]{43,128}")
                || !MessageDigest.isEqual(expected, authorization.substring(7).getBytes(StandardCharsets.UTF_8))) {
            reject(response, 401, "内部服务凭据无效");
            return;
        }
        List<String> tenants = Collections.list(request.getHeaders("tenant-id"));
        if (tenants.size() != 1 || !tenants.get(0).matches("[1-9][0-9]{0,15}")) {
            reject(response, 400, "订单服务需要唯一的正整数租户");
            return;
        }
        long tenant = Long.parseLong(tenants.get(0));
        if (tenant > 9_007_199_254_740_991L) {
            reject(response, 400, "订单服务租户编号超出范围");
            return;
        }
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenant);
            TenantContextHolder.setIgnore(false);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "trade-order-service", null, Collections.singletonList(new SimpleGrantedAuthority(AUTHORITY))));
            response.setHeader("Cache-Control", "no-store");
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.setTenantId(previousTenant);
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), CommonResult.error(status, message));
    }
}
