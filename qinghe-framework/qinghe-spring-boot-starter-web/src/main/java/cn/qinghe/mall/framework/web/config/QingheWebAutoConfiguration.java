package cn.qinghe.mall.framework.web.config;

import cn.qinghe.mall.framework.core.handler.GlobalExceptionHandler;
import cn.qinghe.mall.framework.web.filter.TraceFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import javax.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/** Loads the shared exception boundary in Servlet MVC applications. */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(QingheWebExceptionProperties.class)
public class QingheWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name="traceFilterRegistration")
    public FilterRegistrationBean<TraceFilter> traceFilterRegistration() {
        FilterRegistrationBean<TraceFilter> registration=
                new FilterRegistrationBean<>();
        registration.setFilter(new TraceFilter());
        registration.setName("traceFilter");registration.setOrder(Ordered.HIGHEST_PRECEDENCE+10);
        registration.addUrlPatterns("/*");registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }


    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler(QingheWebExceptionProperties properties) {
        return new GlobalExceptionHandler(properties);
    }
}
