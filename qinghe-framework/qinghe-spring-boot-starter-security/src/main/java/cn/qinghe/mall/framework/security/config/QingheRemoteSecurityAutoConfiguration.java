package cn.qinghe.mall.framework.security.config;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.common.biz.system.permission.PermissionCommonApi;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignApi;
import cn.qinghe.mall.framework.security.core.filter.TenantIdentityFilter;
import cn.qinghe.mall.framework.tenant.core.web.TenantContextWebFilter;
import feign.Feign;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.access.AccessDeniedHandler;
import javax.servlet.DispatcherType;

/** 仅独立业务服务显式启用；system进程内实现保持不变。 */
@AutoConfiguration(before = QingheSecurityAutoConfiguration.class)
@ConditionalOnClass(Feign.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "qinghe.security.remote", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RemoteSecurityProperties.class, SecurityProperties.class,
        org.springframework.boot.autoconfigure.security.SecurityProperties.class})
@EnableFeignClients(clients = SystemSecurityFeignApi.class)
public class QingheRemoteSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "tenantIdentityFilterRegistration")
    public FilterRegistrationBean<TenantIdentityFilter> tenantIdentityFilterRegistration(
            org.springframework.boot.autoconfigure.security.SecurityProperties properties,
            AccessDeniedHandler accessDeniedHandler,
            FilterRegistrationBean<TenantContextWebFilter> tenantContextRegistration) {
        int securityOrder = properties.getFilter().getOrder();
        if (!tenantContextRegistration.isEnabled()
                || tenantContextRegistration.getOrder() >= securityOrder
                || securityOrder == Integer.MAX_VALUE) {
            throw new IllegalStateException("租户与Security过滤器顺序配置不正确");
        }
        FilterRegistrationBean<TenantIdentityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantIdentityFilter(accessDeniedHandler));
        registration.setName("tenantIdentityFilter");
        registration.setOrder(securityOrder + 1);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    @Bean
    public RemoteSystemSecurityClient remoteSystemSecurityClient(SystemSecurityFeignApi api,
            RemoteSecurityProperties properties, SecurityProperties securityProperties) {
        return new RemoteSystemSecurityClient(api, properties, securityProperties);
    }

    @Bean
    @ConditionalOnMissingBean(OAuth2TokenCommonApi.class)
    public OAuth2TokenCommonApi remoteTokenCommonApi(RemoteSystemSecurityClient client) {
        return client::checkAccessToken;
    }

    @Bean
    @ConditionalOnMissingBean(PermissionCommonApi.class)
    public PermissionCommonApi remotePermissionCommonApi(RemoteSystemSecurityClient client) {
        return client::hasAnyPermissions;
    }
}
