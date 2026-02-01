package cn.qinghe.mall.framework.web.config;

import cn.qinghe.mall.framework.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QingheWebAutoConfigurationTest {

    @Test
    void starterDiscoveryMetadataContainsTheActualConfigurationClass() {
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).forEach(candidates::add);
        assertThat(candidates).contains(QingheWebAutoConfiguration.class.getName());
    }

    @Test
    void servletApplicationGetsExactlyOneHandler() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(QingheWebAutoConfiguration.class))
                .run(context -> assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void ordinaryApplicationDoesNotGetTheServletHandler() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(QingheWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void applicationCanSupplyItsOwnHandler() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(QingheWebAutoConfiguration.class))
                .withUserConfiguration(CustomHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context.getBean(GlobalExceptionHandler.class)).isSameAs(context.getBean("customHandler"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHandlerConfiguration {
        @Bean
        GlobalExceptionHandler customHandler() {
            return new GlobalExceptionHandler(new QingheWebExceptionProperties());
        }
    }
}
