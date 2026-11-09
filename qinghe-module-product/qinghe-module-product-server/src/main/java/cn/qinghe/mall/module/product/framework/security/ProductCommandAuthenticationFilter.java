package cn.qinghe.mall.module.product.framework.security;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TradeCommandAuthenticationFilter;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.controller.internal.seckill.InternalSeckillStockController;
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

/**
 * 在product现有单个Token过滤器扩展精确promotion命令；其余路径继续原trade/用户认证。
 * 适配既有TradeCommandAuthenticationFilter凭据/租户/清理规则，不增通用认证层或第二次Servlet注册。
 */
public class ProductCommandAuthenticationFilter extends TradeCommandAuthenticationFilter {
    private final Set<String> paths=new HashSet<>(Arrays.asList(InternalSeckillStockController.ALLOCATE,
            InternalSeckillStockController.RESULT,InternalSeckillStockController.COMPENSATE,InternalSeckillStockController.RETURN));
    private final byte[] promotionToken;
    private final ObjectMapper json;

    public ProductCommandAuthenticationFilter(SecurityProperties security,OAuth2TokenCommonApi tokens,AuthenticationEntryPoint entry,
            ObjectMapper json,String tradeToken,Collection<String> tradePaths,String promotionToken) {
        super(security,tokens,entry,json,tradeToken,tradePaths);
        if (promotionToken==null || !promotionToken.isEmpty() && !promotionToken.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("秒杀库存服务凭据格式不正确");
        if (!promotionToken.isEmpty() && promotionToken.equals(tradeToken))
            throw new IllegalArgumentException("promotion和trade必须使用不同的商品库存服务凭据");
        this.promotionToken=promotionToken.isEmpty()?null:promotionToken.getBytes(StandardCharsets.UTF_8);this.json=json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if (!"POST".equals(request.getMethod()) || !paths.contains(request.getServletPath())) {
            super.doFilterInternal(request,response,chain);return;
        }
        SecurityContextHolder.clearContext();
        if (promotionToken==null) { reject(response,503,"秒杀库存服务入口未配置");return; }
        List<String> authorizations=Collections.list(request.getHeaders("Authorization"));
        String value=authorizations.size()==1?authorizations.get(0):"";
        if (!value.startsWith("Bearer ") || !value.substring(7).matches("[A-Za-z0-9_-]{43,128}")
                || !MessageDigest.isEqual(promotionToken,value.substring(7).getBytes(StandardCharsets.UTF_8))) {
            reject(response,401,"内部秒杀服务凭据无效");return;
        }
        List<String> tenants=Collections.list(request.getHeaders("tenant-id"));
        if (tenants.size()!=1 || !tenants.get(0).matches("[1-9][0-9]{0,15}")) {
            reject(response,400,"秒杀库存服务需要唯一的正整数租户");return;
        }
        long tenant=Long.parseLong(tenants.get(0));
        if (tenant>9_007_199_254_740_991L) { reject(response,400,"秒杀库存租户编号超出范围");return; }
        Long previousTenant=TenantContextHolder.getTenantId();boolean previousIgnore=TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenant);TenantContextHolder.setIgnore(false);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("promotion-seckill-service",null,
                    Collections.singletonList(new SimpleGrantedAuthority("internal:promotion:seckill-stock"))));
            response.setHeader("Cache-Control","no-store");chain.doFilter(request,response);
        } finally {
            SecurityContextHolder.clearContext();TenantContextHolder.setTenantId(previousTenant);TenantContextHolder.setIgnore(previousIgnore);
        }
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");json.writeValue(response.getOutputStream(),CommonResult.error(status,message));
    }
}
