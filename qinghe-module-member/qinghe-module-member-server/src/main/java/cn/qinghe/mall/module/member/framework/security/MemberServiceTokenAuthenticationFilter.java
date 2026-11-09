package cn.qinghe.mall.module.member.framework.security;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.module.member.api.auth.MemberQualificationApi;
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

/** 唯一POST服务入口验证专用Bearer，不调用用户Token检查器以避免system<->member递归。
 * 其余路径完整复用原TokenAuthenticationFilter；服务身份永不构造LoginUser或MEMBER。
 */
public class MemberServiceTokenAuthenticationFilter extends TokenAuthenticationFilter {
    public static final String AUTHORITY="internal:system:member-qualification";
    private final byte[] expected;
    private final ObjectMapper json;
    public MemberServiceTokenAuthenticationFilter(SecurityProperties security,OAuth2TokenCommonApi userTokens,
            AuthenticationEntryPoint entry,ObjectMapper json,MemberServiceAuthProperties properties) {
        super(security,userTokens,entry,json);this.json=json;
        String token=properties.getSystemToken();
        if (token==null || (!token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}"))) {
            throw new IllegalArgumentException("system会员服务凭据配置格式不正确");
        }
        expected=token.isEmpty()?null:token.getBytes(StandardCharsets.UTF_8);
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只跳过这一条持有access的清理入口；Controller/system仍核对凭据，不恢复用户身份。
        return "POST".equals(request.getMethod()) && "/app-api/auth/logout".equals(request.getServletPath());
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if (!"POST".equals(request.getMethod()) || !MemberQualificationApi.PATH.equals(request.getServletPath())) {
            super.doFilterInternal(request,response,chain);return;
        }
        SecurityContextHolder.clearContext();
        if (expected==null) { reject(response,503,"会员服务资格入口未配置");return; }
        List<String> values=Collections.list(request.getHeaders("Authorization"));
        String supplied=values.size()==1?values.get(0):"";
        if (!supplied.startsWith("Bearer ") || !supplied.substring(7).matches("[A-Za-z0-9_-]{43,128}")
                || !MessageDigest.isEqual(expected,supplied.substring(7).getBytes(StandardCharsets.UTF_8))) {
            reject(response,401,"内部服务凭据无效");return;
        }
        try {
            UsernamePasswordAuthenticationToken authentication=new UsernamePasswordAuthenticationToken(
                    "system-member-qualification",null,Collections.singletonList(new SimpleGrantedAuthority(AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request,response);
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setCharacterEncoding("UTF-8");response.setContentType("application/json");
        json.writeValue(response.getOutputStream(),CommonResult.error(status,message));
    }
}
