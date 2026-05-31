package cn.qinghe.mall.framework.tenant.config;

import cn.qinghe.mall.framework.mybatis.config.QingheMybatisAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 Spring 条件装配与现有插件链验证；不启用数据源或 Nacos。 */
class QingheTenantAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("qinghe.info.base-package=cn.qinghe.mall.tenanttests.nomappers")
            // 故意反序传入，验证 AutoConfigurations 会按配置类的顺序约束处理。
            .withConfiguration(AutoConfigurations.of(
                    QingheTenantAutoConfiguration.class, QingheMybatisAutoConfiguration.class));

    @Test
    void starterDiscoveryMetadataShouldContainActualConfigurationClass() {
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .forEach(candidates::add);
        assertThat(candidates).contains(QingheTenantAutoConfiguration.class.getName());
    }

    @Test
    void missingSwitchShouldEnableTenantBeforePaginationOnSameChain() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            assertThat(context).hasSingleBean(TenantLineInnerInterceptor.class);
            assertThat(context).hasSingleBean(TenantProperties.class);
            MybatisPlusInterceptor chain = context.getBean(MybatisPlusInterceptor.class);
            assertThat(chain.getInterceptors()).hasSize(2);
            assertThat(chain.getInterceptors().get(0))
                    .isSameAs(context.getBean(TenantLineInnerInterceptor.class));
            assertThat(chain.getInterceptors().get(1))
                    .isInstanceOf(PaginationInnerInterceptor.class);
        });
    }

    @Test
    void explicitTrueShouldEnableTenantAndBindIgnoredTables() {
        runner.withPropertyValues("qinghe.tenant.enable=true",
                        "qinghe.tenant.ignore-tables[0]=tenant_config_platform")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TenantLineInnerInterceptor.class);
                    assertThat(context.getBean(TenantProperties.class).getIgnoreTables())
                            .containsExactly("tenant_config_platform");
                });
    }

    @Test
    void explicitFalseShouldKeepPaginationWithoutTenantPlugin() {
        runner.withPropertyValues("qinghe.tenant.enable=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TenantLineInnerInterceptor.class);
            assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                    .hasSize(1).allMatch(PaginationInnerInterceptor.class::isInstance);
        });
    }
}
