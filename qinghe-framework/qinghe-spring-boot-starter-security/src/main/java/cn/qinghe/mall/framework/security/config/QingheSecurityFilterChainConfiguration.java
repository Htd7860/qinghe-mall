package cn.qinghe.mall.framework.security.config;

import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.security.PermitAll;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
            AccessDeniedHandler accessDeniedHandler, TokenAuthenticationFilter tokenAuthenticationFilter,
            ObjectProvider<AuthorizeRequestsCustomizer> authorizeRequestsCustomizers,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping
    ) throws Exception {

        // 创建安全链时读取MVC已登记的接口，不在每次请求中反射扫描。
        Map<HttpMethod, Set<String>> permitAllUrls = getPermitAllUrlsFromAnnotations(handlerMapping);

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

                // 仅使用主动Bearer，不接受Cookie/Basic/默认Session身份恢复。
                .csrf(config -> config.disable())

                .exceptionHandling(config -> config
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeRequests(config -> {
                    config.antMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll();
                    permitAllUrls.forEach((method, urls) ->
                            config.antMatchers(method, urls.toArray(new String[0])).permitAll());
                    authorizeRequestsCustomizers.orderedStream()
                            .forEach(customizer -> customizer.customize(config));
                    config.anyRequest().authenticated();
                });

        http.addFilterBefore(
                tokenAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }

    private Map<HttpMethod, Set<String>> getPermitAllUrlsFromAnnotations(
            RequestMappingHandlerMapping handlerMapping) {
        Map<HttpMethod, Set<String>> result = new EnumMap<>(HttpMethod.class);

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            if (!handlerMethod.hasMethodAnnotation(PermitAll.class)
                    && !handlerMethod.getBeanType().isAnnotationPresent(PermitAll.class)) {
                continue;
            }

            // Spring 5.3统一提供两种路径匹配策略的实际映射，含类级路径及MVC前缀。
            Set<String> urls = entry.getKey().getPatternValues();
            if (urls.isEmpty()) {
                continue;
            }
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                // 与本地yudao原版一致：未声明HTTP方法时登记这六种方法。
                methods = EnumSet.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                        RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.PATCH);
            }
            for (RequestMethod requestMethod : methods) {
                HttpMethod method = HttpMethod.valueOf(requestMethod.name());
                result.computeIfAbsent(method, key -> new LinkedHashSet<>()).addAll(urls);
            }
        }
        return result;
    }
}
