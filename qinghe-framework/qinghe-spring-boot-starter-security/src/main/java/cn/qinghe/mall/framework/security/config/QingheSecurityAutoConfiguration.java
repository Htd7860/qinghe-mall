package cn.qinghe.mall.framework.security.config;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.framework.security.core.handler.AccessDeniedHandlerImpl;
import cn.qinghe.mall.framework.security.core.handler.AuthenticationEntryPointImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.config
 * @Project：qinghe-mall
 * @name：QingheSecurityAutoConfiguration
 * @Date：2026/10/22 20:14
 * @Filename：QingheSecurityAutoConfiguration
 */

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
@EnableConfigurationProperties(SecurityProperties.class)
public class QingheSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper){
        return new AuthenticationEntryPointImpl(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    public AccessDeniedHandler accessDeniedHandler(
            ObjectMapper objectMapper) {
        return new AccessDeniedHandlerImpl(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(TokenAuthenticationFilter.class)
    public TokenAuthenticationFilter tokenAuthenticationFilter(
            SecurityProperties properties,
            OAuth2TokenCommonApi tokenApi,
            AuthenticationEntryPoint entryPoint,
            ObjectMapper objectMapper) {

        return new TokenAuthenticationFilter(
                properties, tokenApi, entryPoint, objectMapper);

    }

    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter>
    tokenAuthenticationFilterRegistration(
            TokenAuthenticationFilter filter) {

        FilterRegistrationBean<TokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

}
