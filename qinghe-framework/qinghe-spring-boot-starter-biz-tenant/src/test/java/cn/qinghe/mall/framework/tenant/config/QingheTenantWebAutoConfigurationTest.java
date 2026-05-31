package cn.qinghe.mall.framework.tenant.config;

import cn.qinghe.mall.framework.common.enums.WebFilterOrderEnum;
import cn.qinghe.mall.framework.tenant.core.web.TenantContextWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QingheTenantWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    QingheTenantWebAutoConfiguration.class, JacksonAutoConfiguration.class));

    @Test
    void importsShouldContainServletConfiguration() {
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .forEach(candidates::add);
        assertThat(candidates).contains(QingheTenantWebAutoConfiguration.class.getName());
    }

    @Test
    void servletContextShouldCreateExactlyOneRegistrationByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(TenantContextWebFilter.class);
            assertThat(registration.getOrder()).isEqualTo(WebFilterOrderEnum.TENANT_CONTEXT_FILTER);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
    }

    @Test
    void explicitTrueShouldEnableServletRegistration() {
        runner.withPropertyValues("qinghe.tenant.enable=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
        });
    }

    @Test
    void explicitFalseShouldDisableServletRegistration() {
        runner.withPropertyValues("qinghe.tenant.enable=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }

    @Test
    void nonWebContextShouldNotCreateServletRegistration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QingheTenantWebAutoConfiguration.class, JacksonAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }
}
