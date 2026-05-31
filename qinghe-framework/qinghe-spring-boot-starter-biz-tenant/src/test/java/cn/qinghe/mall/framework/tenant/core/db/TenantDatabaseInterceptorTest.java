package cn.qinghe.mall.framework.tenant.core.db;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 使用真实 MP 实体元数据验证规则，不连接数据库。 */
class TenantDatabaseInterceptorTest {

    private TenantProperties properties;

    @BeforeAll
    static void registerEntityMetadata() {
        register(TenantRecord.class);
        register(PlatformRecord.class);
        register(FilteringRecord.class);
        register(InvalidRecord.class);
    }

    private static void register(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "tenant-rule-test/" + entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        properties = new TenantProperties();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantExpressionShouldUseCurrentRequiredTenant() {
        TenantContextHolder.setTenantId(101L);
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.getTenantId()).isInstanceOf(LongValue.class);
        assertThat(((LongValue) handler.getTenantId()).getValue()).isEqualTo(101L);
        TenantContextHolder.setTenantId(202L);
        assertThat(((LongValue) handler.getTenantId()).getValue()).isEqualTo(202L);
        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
    }

    @Test
    void missingTenantShouldFailInsteadOfUsingDefaultTenant() {
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThrows(IllegalStateException.class, handler::getTenantId);
    }

    @Test
    void unknownTableShouldNotBypassFiltering() {
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.ignoreTable("tenant_rule_unmapped_table")).isFalse();
    }

    @Test
    void trustedIgnoreContextShouldApplyOnlyUntilCleared() {
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        TenantContextHolder.setIgnore(true);
        assertThat(handler.ignoreTable("tenant_rule_record")).isTrue();
        TenantContextHolder.clear();
        assertThat(handler.ignoreTable("tenant_rule_record")).isFalse();
    }

    @Test
    void configuredTableNamesShouldBeNormalized() {
        properties.setIgnoreTables(Collections.singleton("`TENANT_RULE_EXPLICIT_PLATFORM`"));
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.ignoreTable("tenant_rule_explicit_platform")).isTrue();
        assertThat(handler.ignoreTable("`TENANT_RULE_EXPLICIT_PLATFORM`")).isTrue();
        assertThat(handler.ignoreTable("tenant_rule_other")).isFalse();
    }

    @Test
    void laterConfigurationMutationShouldNotChangeExistingHandler() {
        properties.setIgnoreTables(new HashSet<>(Collections.singleton("tenant_rule_old_platform")));
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        properties.getIgnoreTables().clear();
        properties.getIgnoreTables().add("tenant_rule_new_platform");
        assertThat(handler.ignoreTable("tenant_rule_old_platform")).isTrue();
        assertThat(handler.ignoreTable("tenant_rule_new_platform")).isFalse();
    }

    @Test
    void nullIgnoreTableConfigurationShouldStillFilterUnknownTables() {
        properties.setIgnoreTables(null);
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.ignoreTable("tenant_rule_unmapped_table")).isFalse();
    }

    @Test
    void tenantBaseEntityShouldTakePriorityOverConfigurationAndAnnotation() {
        properties.setIgnoreTables(Collections.singleton("tenant_rule_record"));
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.ignoreTable("tenant_rule_record")).isFalse();
    }

    @Test
    void platformEntityAnnotationShouldAllowExplicitException() {
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.ignoreTable("tenant_rule_platform")).isTrue();
    }

    @Test
    void falseEntityAnnotationShouldRetainFiltering() {
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThat(handler.ignoreTable("tenant_rule_filtering")).isFalse();
    }

    @Test
    void unsupportedEntityExpressionShouldFailInsteadOfBypassingFiltering() {
        TenantDatabaseInterceptor handler = new TenantDatabaseInterceptor(properties);
        assertThrows(IllegalStateException.class,
                () -> handler.ignoreTable("tenant_rule_invalid"));
    }

    @TenantIgnore
    @TableName("tenant_rule_record")
    static class TenantRecord extends TenantBaseDo {
        @TableId
        private Long id;
    }

    @TenantIgnore
    @TableName("tenant_rule_platform")
    static class PlatformRecord {
        @TableId
        private Long id;
    }

    @TenantIgnore(enable = "false")
    @TableName("tenant_rule_filtering")
    static class FilteringRecord {
        @TableId
        private Long id;
    }

    @TenantIgnore(enable = "#{true}")
    @TableName("tenant_rule_invalid")
    static class InvalidRecord {
        @TableId
        private Long id;
    }
}
