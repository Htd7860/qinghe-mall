package cn.qinghe.mall.module.member.framework.security;
import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.web.AuthenticationEntryPoint;
/** 使用已有条件Bean扩展点和禁Servlet重复注册机制，不另建过滤器链。 */
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(MemberServiceAuthProperties.class)
public class MemberSecurityConfiguration {
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(SecurityProperties properties,OAuth2TokenCommonApi userTokens,
            AuthenticationEntryPoint entry,ObjectMapper json,MemberServiceAuthProperties services) {
        return new MemberServiceTokenAuthenticationFilter(properties,userTokens,entry,json,services);
    }
}
