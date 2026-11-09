package cn.qinghe.mall.module.system.dal.mysql.oauth2;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** User-run MySQL tests; never starts system, Nacos, Redis or a browser endpoint. */
@Tag("token-mysql")
@EnabledIfEnvironmentVariable(named = "QINGHE_SYSTEM_TEST_ENABLED", matches = "true")
@Execution(ExecutionMode.SAME_THREAD)
class OAuth2TokenMapperMySqlTest {

    private static final LocalDateTime EXPIRY = LocalDateTime.of(2030, 1, 2, 3, 4, 5, 123456000);
    private static DriverManagerDataSource dataSource;
    private static SqlSessionFactory sessionFactory;
    private static boolean accessCreated;
    private static boolean refreshCreated;

    enum Kind {
        ACCESS("system_oauth2_access_token", "access_token"),
        REFRESH("system_oauth2_refresh_token", "refresh_token");

        final String table;
        final String tokenColumn;
        Kind(String table, String tokenColumn) {
            this.table = table;
            this.tokenColumn = tokenColumn;
        }
    }

    @BeforeAll
    static void createOwnedFixturesAndLoadRealMappers() throws Exception {
        String url = environment("QINGHE_SYSTEM_TEST_URL");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/qinghe_system_test(\\?.*)?")) {
            throw new IllegalStateException("Only local qinghe_system_test is allowed; application DB is forbidden");
        }
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(environment("QINGHE_SYSTEM_TEST_USERNAME"));
        dataSource.setPassword(environment("QINGHE_SYSTEM_TEST_PASSWORD"));
        try (Connection connection = checkedConnection()) {
            for (Kind kind : Kind.values()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
                    statement.setString(1, kind.table);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        if (result.getInt(1) != 0) {
                            throw new IllegalStateException("Fixture already exists; refusing to take over: " + kind.table);
                        }
                    }
                }
            }
            createTable(connection, Kind.REFRESH);
            refreshCreated = true;
            createTable(connection, Kind.ACCESS);
            accessCreated = true;
        }

        MybatisPlusInterceptor chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new TenantLineInnerInterceptor(
                new TenantDatabaseInterceptor(new TenantProperties())));
        chain.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(globalConfig);
        factory.setPlugins(chain);
        sessionFactory = factory.getObject();
        sessionFactory.getConfiguration().addMapper(OAuth2AccessTokenMapper.class);
        sessionFactory.getConfiguration().addMapper(OAuth2RefreshTokenMapper.class);
        assertThat(TableInfoHelper.getTableInfo(OAuth2AccessTokenDO.class))
                .as("Access mapper must extend BaseMapperX<OAuth2AccessTokenDO>").isNotNull();
        assertThat(TableInfoHelper.getTableInfo(OAuth2RefreshTokenDO.class))
                .as("Refresh mapper must extend BaseMapperX<OAuth2RefreshTokenDO>").isNotNull();
    }

    private static void createTable(Connection connection, Kind kind) throws Exception {
        String ddl;
        try (java.io.InputStream input = new ClassPathResource("db/" + kind.table + ".sql").getInputStream()) {
            ddl = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private static Connection checkedConnection() throws Exception {
        Connection connection = dataSource.getConnection();
        if (!"qinghe_system_test".equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalStateException("Actual catalog is not qinghe_system_test; refusing all fixture operations");
        }
        return connection;
    }

    @BeforeEach
    void resetOnlyTablesCreatedByThisRun() throws Exception {
        TenantContextHolder.clear();
        if (!accessCreated || !refreshCreated) {
            throw new IllegalStateException("Fixture ownership was not established");
        }
        try (Connection connection = checkedConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM system_oauth2_access_token");
            statement.executeUpdate("DELETE FROM system_oauth2_refresh_token");
        }
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @AfterAll
    static void dropOnlyOwnedFixtures() throws Exception {
        TenantContextHolder.clear();
        if (dataSource == null || (!accessCreated && !refreshCreated)) {
            return;
        }
        try (Connection connection = checkedConnection(); Statement statement = connection.createStatement()) {
            if (accessCreated) {
                statement.execute("DROP TABLE system_oauth2_access_token");
                accessCreated = false;
            }
            if (refreshCreated) {
                statement.execute("DROP TABLE system_oauth2_refresh_token");
                refreshCreated = false;
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void generatedIdJsonAndMicrosecondTimeShouldRoundTrip(Kind kind) {
        TenantContextHolder.setTenantId(101L);
        TenantBaseDo record = record(kind, "round-trip", 101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            BaseMapperX<TenantBaseDo> mapper = mapper(session, kind);
            assertThat(mapper.insert(record)).isEqualTo(1);
            assertThat(id(record)).isNotNull().isPositive();
        }
        // A separate session avoids mistaking MyBatis's first-level cache for a DB round trip.
        try (SqlSession session = sessionFactory.openSession(true)) {
            TenantBaseDo restored = mapper(session, kind).selectById(id(record));
            assertThat(restored).isNotSameAs(record).isExactlyInstanceOf(record.getClass());
            assertThat(restored.getTenantId()).isEqualTo(101L);
            if (kind == Kind.ACCESS) {
                OAuth2AccessTokenDO token = (OAuth2AccessTokenDO) restored;
                assertThat(token.getScopes()).containsExactly("product:read", "order:read");
                assertThat(token.getUserInfo()).containsEntry("nickname", "青禾测试员");
                assertThat(token.getExpiresTime()).isEqualTo(EXPIRY);
            } else {
                OAuth2RefreshTokenDO token = (OAuth2RefreshTokenDO) restored;
                assertThat(token.getScopes()).containsExactly("product:read", "order:read");
                assertThat(token.getExpiresTime()).isEqualTo(EXPIRY);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void revokedTimeShouldBeStoredButDoesNotItselfFilterGenericMapperQueries(Kind kind) {
        TenantContextHolder.setTenantId(101L);
        TenantBaseDo record = record(kind, "revoke", 101L);
        LocalDateTime revoked = EXPIRY.minusDays(1);
        try (SqlSession session = sessionFactory.openSession(true)) {
            BaseMapperX<TenantBaseDo> mapper = mapper(session, kind);
            mapper.insert(record);
            assertThat(mapper.update(null, new UpdateWrapper<TenantBaseDo>()
                    .eq("id", id(record)).set("revoked_time", revoked))).isEqualTo(1);
        }
        try (SqlSession session = sessionFactory.openSession(true)) {
            TenantBaseDo restored = mapper(session, kind).selectById(id(record));
            assertThat(restored).isNotNull();
            LocalDateTime actual = kind == Kind.ACCESS
                    ? ((OAuth2AccessTokenDO) restored).getRevokedTime()
                    : ((OAuth2RefreshTokenDO) restored).getRevokedTime();
            assertThat(actual).isEqualTo(revoked);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void logicalDeleteShouldHideRecordWhileKeepingDatabaseRow(Kind kind) throws Exception {
        TenantContextHolder.setTenantId(101L);
        TenantBaseDo record = record(kind, "delete", 101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            BaseMapperX<TenantBaseDo> mapper = mapper(session, kind);
            mapper.insert(record);
            assertThat(mapper.deleteById(id(record))).isEqualTo(1);
        }
        try (SqlSession session = sessionFactory.openSession(true)) {
            assertThat(mapper(session, kind).selectById(id(record))).isNull();
        }
        try (Connection connection = checkedConnection(); PreparedStatement statement =
                connection.prepareStatement("SELECT deleted+0 FROM " + kind.table + " WHERE id=?")) {
            statement.setLong(1, id(record));
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void foreignTenantReadUpdateAndDeleteShouldBeBlocked(Kind kind) {
        TenantContextHolder.setTenantId(202L);
        TenantBaseDo foreign = record(kind, "foreign", 202L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            mapper(session, kind).insert(foreign);
        }
        TenantContextHolder.setTenantId(101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            BaseMapperX<TenantBaseDo> mapper = mapper(session, kind);
            assertThat(mapper.selectById(id(foreign))).isNull();
            assertThat(mapper.update(null, new UpdateWrapper<TenantBaseDo>()
                    .eq("id", id(foreign)).set("revoked_time", EXPIRY))).isZero();
            assertThat(mapper.deleteById(id(foreign))).isZero();
            assertThat(mapper.selectCount(new QueryWrapper<TenantBaseDo>())).isZero();
        }
        TenantContextHolder.setTenantId(202L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            TenantBaseDo retained = mapper(session, kind).selectById(id(foreign));
            assertThat(retained).isNotNull();
            assertThat(kind == Kind.ACCESS ? ((OAuth2AccessTokenDO) retained).getRevokedTime()
                    : ((OAuth2RefreshTokenDO) retained).getRevokedTime()).isNull();
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void missingTenantShouldFailClosed(Kind kind) {
        TenantContextHolder.clear();
        try (SqlSession session = sessionFactory.openSession(true)) {
            assertThatThrownBy(() -> mapper(session, kind).selectCount(new QueryWrapper<TenantBaseDo>()))
                    .isInstanceOf(RuntimeException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void exactDuplicateShouldFailButCaseDistinctCredentialShouldRemainDistinct(Kind kind) {
        TenantContextHolder.setTenantId(101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            BaseMapperX<TenantBaseDo> mapper = mapper(session, kind);
            assertThat(mapper.insert(record(kind, "CaseToken", 101L))).isEqualTo(1);
            assertThat(mapper.insert(record(kind, "casetoken", 101L))).isEqualTo(1);
            assertThat(mapper.selectCount(new QueryWrapper<TenantBaseDo>())).isEqualTo(2L);
            assertThat(mapper.selectCount(new QueryWrapper<TenantBaseDo>()
                    .eq(kind.tokenColumn, "test-CaseToken"))).isEqualTo(1L);
            assertThatThrownBy(() -> mapper.insert(record(kind, "CaseToken", 101L)))
                    .isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BaseMapperX<TenantBaseDo> mapper(SqlSession session, Kind kind) {
        // One test harness for two concrete production mappers; entity metadata remains their own.
        return (BaseMapperX) (kind == Kind.ACCESS
                ? session.getMapper(OAuth2AccessTokenMapper.class)
                : session.getMapper(OAuth2RefreshTokenMapper.class));
    }

    private static Long id(TenantBaseDo record) {
        return record instanceof OAuth2AccessTokenDO
                ? ((OAuth2AccessTokenDO) record).getId() : ((OAuth2RefreshTokenDO) record).getId();
    }

    private static TenantBaseDo record(Kind kind, String suffix, Long tenantId) {
        TenantBaseDo record;
        if (kind == Kind.ACCESS) {
            OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
            token.setAccessToken("test-" + suffix);
            token.setRefreshToken("test-refresh-" + suffix);
            token.setUserId(11L);
            token.setUserType(2);
            token.setClientId("test-admin");
            token.setScopes(Arrays.asList("product:read", "order:read"));
            token.setUserInfo(Collections.singletonMap("nickname", "青禾测试员"));
            token.setExpiresTime(EXPIRY);
            record = token;
        } else {
            OAuth2RefreshTokenDO token = new OAuth2RefreshTokenDO();
            token.setRefreshToken("test-" + suffix);
            token.setUserId(11L);
            token.setUserType(2);
            token.setClientId("test-admin");
            token.setScopes(Arrays.asList("product:read", "order:read"));
            token.setExpiresTime(EXPIRY);
            record = token;
        }
        record.setTenantId(tenantId);
        record.setDeleted(false);
        // Audit auto-fill is not implemented yet; fixtures supply all required audit values explicitly.
        record.setCreator("test");
        record.setUpdater("test");
        record.setCreateTime(EXPIRY.minusDays(2));
        record.setUpdateTime(EXPIRY.minusDays(2));
        return record;
    }
}
