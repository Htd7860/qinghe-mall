package cn.qinghe.mall.framework.tenant.config;

import cn.qinghe.mall.framework.common.enums.WebFilterOrderEnum;
import cn.qinghe.mall.framework.tenant.core.web.TenantContextWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.DispatcherType;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.config
 * @Project：qinghe-mall
 * @name：QingheTenantWebAutoConfiguration
 * @Date：2026/10/22 16:53
 * @Filename：QingheTenantWebAutoConfiguration
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
@ConditionalOnProperty(
        prefix = "qinghe.tenant",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class QingheTenantWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilterFilterRegistrationBean(ObjectMapper objectMapper){
        FilterRegistrationBean<TenantContextWebFilter> registration=new FilterRegistrationBean<>();
        registration.setFilter(new TenantContextWebFilter(objectMapper));
        registration.setName("tenantContextWebFilter");
        registration.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

}
