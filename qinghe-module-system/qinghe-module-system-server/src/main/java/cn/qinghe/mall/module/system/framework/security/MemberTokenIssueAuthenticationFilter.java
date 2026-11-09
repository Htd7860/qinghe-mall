package cn.qinghe.mall.module.system.framework.security;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.module.system.api.oauth2.MemberTokenIssueApi;
import cn.qinghe.mall.module.system.api.tenant.MemberStorefrontApi;
import cn.qinghe.mall.module.system.framework.member.MemberQualificationProperties;
import cn.qinghe.mall.module.system.framework.member.MemberTokenIssueProperties;
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
import java.util.Collections;
import java.util.List;

/** 仅精确会员认证POST使用专用服务身份；普通用户路径继续原Bearer校验。
 * 不构造LoginUser，不能把服务密钥用作员工或会员Token。
 */
public class MemberTokenIssueAuthenticationFilter extends TokenAuthenticationFilter {
    public static final String AUTHORITY="internal:member:token-issue";
    public static final String STOREFRONT_AUTHORITY="internal:member:storefront";
    public static final String REFRESH_AUTHORITY="internal:member:token-refresh";
    public static final String LOGOUT_AUTHORITY="internal:member:token-logout";
    private final byte[] expected;
    private final byte[] storefrontReadExpected;
    private final byte[] productReadExpected;
    private final ObjectMapper json;
    public MemberTokenIssueAuthenticationFilter(SecurityProperties security,OAuth2TokenCommonApi tokens,
            AuthenticationEntryPoint entry,ObjectMapper json,MemberTokenIssueProperties settings,
            MemberQualificationProperties qualification) {
        super(security,tokens,entry,json);this.json=json;
        String value=settings.getMemberToken();
        if (value==null || !value.isEmpty() && !value.matches("[A-Za-z0-9_-]{43,128}")) {
            throw new IllegalArgumentException("会员签发服务凭据配置格式不正确");
        }
        if (!value.isEmpty() && value.equals(qualification.getSystemToken())) {
            throw new IllegalArgumentException("会员签发与资格凭据必须分开配置");
        }
        expected=value.isEmpty()?null:value.getBytes(StandardCharsets.UTF_8);
        String read=settings.getPromotionStorefrontToken();
        if(read==null || !read.isEmpty() && !read.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("商城只读入口凭据配置格式不正确");
        if(!read.isEmpty() && (read.equals(value) || read.equals(qualification.getSystemToken())))
            throw new IllegalArgumentException("商城只读入口凭据必须与会员签发及资格凭据分开");
        storefrontReadExpected=read.isEmpty()?null:read.getBytes(StandardCharsets.UTF_8);
        String productRead=settings.getProductStorefrontToken();
        if(productRead==null || !productRead.isEmpty() && !productRead.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("商品目录只读入口凭据格式不正确");
        if(!productRead.isEmpty() && (productRead.equals(value) || productRead.equals(qualification.getSystemToken())))
            throw new IllegalArgumentException("商品目录只读凭据必须与会员签发及资格凭据分开");
        productReadExpected=productRead.isEmpty()?null:productRead.getBytes(StandardCharsets.UTF_8);
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath();
        return "POST".equals(request.getMethod()) && ("/admin-api/auth/logout".equals(path)
                || "/platform-api/auth/logout".equals(path));
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        boolean issue=MemberTokenIssueApi.PATH.equals(request.getServletPath());
        boolean storefront=MemberStorefrontApi.PATH.equals(request.getServletPath());
        boolean refresh=MemberTokenIssueApi.REFRESH_PATH.equals(request.getServletPath());
        boolean logout=MemberTokenIssueApi.LOGOUT_PATH.equals(request.getServletPath());
        if (!"POST".equals(request.getMethod()) || !(issue || storefront || refresh || logout)) {
            super.doFilterInternal(request,response,chain);return;
        }
        SecurityContextHolder.clearContext();
        if (expected==null && !(storefront && (storefrontReadExpected!=null || productReadExpected!=null))) { reject(response,503,"会员内部认证入口尚未配置");return; }
        List<String> values=Collections.list(request.getHeaders("Authorization"));
        String supplied=values.size()==1?values.get(0):"";
        byte[] presented=supplied.startsWith("Bearer ")?supplied.substring(7).getBytes(StandardCharsets.UTF_8):new byte[0];
        boolean memberMatch=expected!=null && MessageDigest.isEqual(expected,presented);
        boolean readMatch=storefront && storefrontReadExpected!=null && MessageDigest.isEqual(storefrontReadExpected,presented);
        boolean productMatch=storefront && productReadExpected!=null && MessageDigest.isEqual(productReadExpected,presented);
        if (!supplied.startsWith("Bearer ") || !supplied.substring(7).matches("[A-Za-z0-9_-]{43,128}")
                || !(memberMatch || readMatch || productMatch)) {
            reject(response,401,"内部服务凭据无效");return;
        }
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    productMatch?"product-storefront-service":readMatch?"promotion-storefront-service":"member-auth-service",null,Collections.singletonList(new SimpleGrantedAuthority(
                            issue?AUTHORITY:storefront?STOREFRONT_AUTHORITY:refresh?REFRESH_AUTHORITY:LOGOUT_AUTHORITY))));
            chain.doFilter(request,response);
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");json.writeValue(response.getOutputStream(),CommonResult.error(status,message));
    }
}
