package cn.qinghe.mall.framework.security.config;

import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.config
 * @Project：qinghe-mall
 * @name：QingheSecurityFilterChainConfiguration
 * @Date：2026/10/22 20:39
 * @Filename：QingheSecurityFilterChainConfiguration
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class,
        after = QingheSecurityAutoConfiguration.class)
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class QingheSecurityFilterChainConfiguration {
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler, TokenAuthenticationFilter tokenAuthenticationFilter
    ) throws Exception {

        http
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())

                .sessionManagement(config -> config
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .securityContext(config -> config
                        .securityContextRepository(
                                new NullSecurityContextRepository()))

                .requestCache(config -> config
                        .requestCache(new NullRequestCache()))

                // 仅内部 Bearer API 链不使用 CSRF；
                // 浏览器 Cookie 入口仍需 CSRF 防护。
                .csrf(config -> config.disable())

                .exceptionHandling(config -> config
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeRequests(config -> config
                        .antMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated());

        http.addFilterBefore(
                tokenAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}
