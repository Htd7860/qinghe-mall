package cn.qinghe.mall.module.system.framework.security;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.framework.tenant.core.web.TenantContextWebFilter;
import cn.qinghe.mall.module.system.framework.member.MemberTokenIssueProperties;
import cn.qinghe.mall.module.system.framework.member.MemberQualificationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.DispatcherType;

/** 将system租户身份桥接注册到内部Bearer安全链之后。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityProperties.class, LoginFailureLimitProperties.class, MemberTokenIssueProperties.class})
public class SystemSecurityConfiguration {

    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(
            cn.qinghe.mall.framework.security.config.SecurityProperties properties,
            OAuth2TokenCommonApi tokenApi, AuthenticationEntryPoint entryPoint,
            ObjectMapper objectMapper, MemberTokenIssueProperties memberTokens,
            MemberQualificationProperties qualification) {
        // 使用starter提供的条件Bean扩展点；普通接口继续使用原过滤器逻辑。
        return new MemberTokenIssueAuthenticationFilter(properties, tokenApi, entryPoint,
                objectMapper, memberTokens, qualification);
    }

    @Bean
    public FilterRegistrationBean<TenantIdentityFilter> tenantIdentityFilterRegistration(
            SecurityProperties securityProperties,
            AccessDeniedHandler accessDeniedHandler,
            FilterRegistrationBean<TenantContextWebFilter> tenantContextRegistration) {
        int securityOrder = securityProperties.getFilter().getOrder();
        if (!tenantContextRegistration.isEnabled()
                || tenantContextRegistration.getOrder() >= securityOrder
                || securityOrder == Integer.MAX_VALUE) {
            throw new IllegalStateException("租户上下文与安全过滤器顺序配置不正确");
        }

        FilterRegistrationBean<TenantIdentityFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(new TenantIdentityFilter(accessDeniedHandler));
        registration.setName("tenantIdentityFilter");
        registration.setOrder(securityOrder + 1);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
