package cn.qinghe.mall.framework.security.core.filter;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.core.filter
 * @Project：qinghe-mall
 * @name：TokenAuthenticationFilter
 * @Date：2026/10/22 21:16
 * @Filename：TokenAuthenticationFilter
 */

//用于token校验
@RequiredArgsConstructor
@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {


    private final SecurityProperties securityProperties;
    private final OAuth2TokenCommonApi tokenApi;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final ObjectMapper objectMapper;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader());
            if(token!=null){
                OAuth2AccessTokenCheckRespDTO result=checkAccessToken(token);
                LoginUser loginUser=new LoginUser();
                loginUser.setId(result.getUserId());
                loginUser.setUserType(result.getUserType());
                loginUser.setInfo(result.getUserInfo());
                loginUser.setTenantId(result.getTenantId());
                loginUser.setScopes(result.getScopes());
                loginUser.setExpiresTime(result.getExpiresTime());

                SecurityFrameworkUtils.setLoginUser(loginUser,request);
            }
        } catch (AuthenticationServiceException e) {
            SecurityContextHolder.clearContext();
            log.error("Token verification service unavailable");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            objectMapper.writeValue(
                    response.getOutputStream(),
                    CommonResult.error(
                            HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "认证服务暂不可用"
                    )
            );
            return;
        }catch (AuthenticationException ex){
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, ex);
            return;
        }
      filterChain.doFilter(request,response);
    }

    private OAuth2AccessTokenCheckRespDTO checkAccessToken(String token) {
        CommonResult<OAuth2AccessTokenCheckRespDTO> response;

        try {
            response = tokenApi.checkAccessToken(token);
        } catch (ServiceException ex) {
            if (GlobalErrorCodeConstants.UNAUTHORIZED.getCode()
                    .equals(ex.getCode())) {
                throw new BadCredentialsException("访问令牌无效");
            }
            throw new AuthenticationServiceException("令牌校验服务异常", ex);
        } catch (RuntimeException ex) {
            throw new AuthenticationServiceException("令牌校验服务异常", ex);
        }

        if (response == null || response.getCode() == null) {
            throw new AuthenticationServiceException("令牌校验服务返回异常");
        }

        if (GlobalErrorCodeConstants.UNAUTHORIZED.getCode()
                .equals(response.getCode())) {
            throw new BadCredentialsException("访问令牌无效");
        }

        if (!response.isSuccess()) {
            throw new AuthenticationServiceException("令牌校验服务返回异常");
        }

        OAuth2AccessTokenCheckRespDTO result = response.getData();
        if (result == null) {
            throw new BadCredentialsException("访问令牌无效");
        }

        if (result.getUserId() == null || result.getUserId() <= 0
                || result.getUserType() == null
                || UserTypeEnum.valueOf(result.getUserType()) == null
                || result.getTenantId() == null || result.getTenantId() < 0
                || result.getExpiresTime() == null) {
            throw new AuthenticationServiceException("令牌身份数据不完整");
        }

        if (!result.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw new BadCredentialsException("访问令牌已过期");
        }

        return result;
    }
}
