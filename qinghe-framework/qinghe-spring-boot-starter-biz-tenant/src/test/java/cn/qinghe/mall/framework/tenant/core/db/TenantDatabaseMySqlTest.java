package cn.qinghe.mall.framework.tenant.core.db;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 用户显式提供独立本机测试库后才运行。只管理本次创建的两张测试表。
 * 两项 tenant-write-safety 反例按安全目标断言，当前未实现写入保护时应失败。
 */
@Tag("tenant-mysql")
@EnabledIfEnvironmentVariable(named = "QINGHE_TENANT_TEST_URL", matches = ".+")
class TenantDatabaseMySqlTest {

    private static final String RECORD_TABLE = "tenant_isolation_test_record";
    private static final String PLATFORM_TABLE = "tenant_isolation_test_platform";
    private static DriverManagerDataSource dataSource;
    private static SqlSessionFactory sessionFactory;
    private static boolean recordTableCreated;
    private static boolean platformTableCreated;

    @BeforeAll
    static void initializeDedicatedDatabaseFixtures() throws Exception {
        recordTableCreated = false;
        platformTableCreated = false;
        sessionFactory = null;
        String url = requiredEnvironment("QINGHE_TENANT_TEST_URL");
        if (!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/qinghe_tenant_test(\\?.*)?")) {
            throw new IllegalStateException("测试只接受本机3306端口的qinghe_tenant_test独立库");
        }
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(requiredEnvironment("QINGHE_TENANT_TEST_USERNAME"));
        dataSource.setPassword(requiredEnvironment("QINGHE_TENANT_TEST_PASSWORD"));

        try (Connection connection = checkedConnection()) {
            assertTableAbsent(connection, RECORD_TABLE);
            assertTableAbsent(connection, PLATFORM_TABLE);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE " + RECORD_TABLE + " ("
                        + "id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,"
                        + "name VARCHAR(64) NOT NULL, deleted BIT(1) NOT NULL DEFAULT b'0',"
                        + "create_time DATETIME NULL, update_time DATETIME NULL,"
                        + "creator VARCHAR(64) NULL, updater VARCHAR(64) NULL,"
                        + "KEY idx_tenant (tenant_id)) ENGINE=InnoDB");
                recordTableCreated = true;
                statement.execute("CREATE TABLE " + PLATFORM_TABLE
                        + " (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL) ENGINE=InnoDB");
                platformTableCreated = true;
            }
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
        sessionFactory.getConfiguration().addMapper(RecordMapper.class);
        sessionFactory.getConfiguration().addMapper(PlatformMapper.class);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("缺少测试环境变量：" + name);
        }
        return value;
    }

    private static Connection checkedConnection() throws Exception {
        Connection connection = dataSource.getConnection();
        if (!"qinghe_tenant_test".equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalStateException("实际连接不是独立租户测试库，拒绝操作");
        }
        return connection;
    }

    private static void assertTableAbsent(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) != 0) {
                    throw new IllegalStateException("测试表已存在，拒绝清空或接管：" + table);
                }
            }
        }
    }

    @BeforeEach
    void resetOwnedFixtures() throws Exception {
        TenantContextHolder.clear();
        try (Connection connection = checkedConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + RECORD_TABLE);
            statement.executeUpdate("DELETE FROM " + PLATFORM_TABLE);
            statement.executeUpdate("INSERT INTO " + RECORD_TABLE + " (id,tenant_id,name) VALUES "
                    + "(1,101,'A-one'),(2,101,'A-two'),(3,202,'B-one'),(4,202,'B-two')");
            statement.executeUpdate("INSERT INTO " + PLATFORM_TABLE + " VALUES (1,'platform')");
        }
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @AfterAll
    static void dropOnlyTablesCreatedByThisRun() throws Exception {
        TenantContextHolder.clear();
        if (!recordTableCreated && !platformTableCreated) {
            return;
        }
        try (Connection connection = checkedConnection(); Statement statement = connection.createStatement()) {
            if (platformTableCreated) {
                statement.execute("DROP TABLE " + PLATFORM_TABLE);
            }
            if (recordTableCreated) {
                statement.execute("DROP TABLE " + RECORD_TABLE);
            }
        }
    }

    @Test
    void selectingForeignIdShouldReturnNoRecord() {
        TenantContextHolder.setTenantId(101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertThat(mapper.selectById(1L)).isNotNull();
            assertThat(mapper.selectById(3L)).isNull();
        }
    }

    @Test
    void listAndCountShouldContainOnlyCurrentTenant() {
        TenantContextHolder.setTenantId(202L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertThat(mapper.selectList(new QueryWrapper<Record>().orderByAsc("id")))
                    .extracting(Record::getId).containsExactly(3L, 4L);
            assertThat(mapper.selectCount(new QueryWrapper<Record>())).isEqualTo(2L);
        }
    }

    @Test
    void pageAndCountShouldUseSameTenantBoundary() {
        TenantContextHolder.setTenantId(101L);
        PageParam page = new PageParam();
        page.setPageNo(2);
        page.setPageSize(1);
        try (SqlSession session = sessionFactory.openSession(true)) {
            PageResult<Record> result = session.getMapper(RecordMapper.class)
                    .selectPage(page, new QueryWrapper<Record>().orderByAsc("id"));
            assertThat(result.getTotal()).isEqualTo(2L);
            assertThat(result.getList()).extracting(Record::getId).containsExactly(2L);
        }
    }

    @Test
    void updateShouldNotModifyForeignTenant() throws Exception {
        TenantContextHolder.setTenantId(101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertThat(mapper.update(null,
                    new UpdateWrapper<Record>().eq("id", 3L).set("name", "intrusion"))).isZero();
            assertThat(mapper.update(null,
                    new UpdateWrapper<Record>().eq("id", 1L).set("name", "A-updated"))).isEqualTo(1);
        }
        assertThat(readColumn(3L, "name")).isEqualTo("B-one");
        assertThat(readColumn(1L, "name")).isEqualTo("A-updated");
    }

    @Test
    void logicalDeleteShouldBeRestrictedToCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            RecordMapper mapper = session.getMapper(RecordMapper.class);
            assertThat(mapper.deleteById(3L)).isZero();
            assertThat(mapper.deleteById(1L)).isEqualTo(1);
            assertThat(mapper.selectById(1L)).isNull();
        }
        assertThat(readColumn(1L, "deleted+0")).isEqualTo("1");
        assertThat(readColumn(3L, "deleted+0")).isEqualTo("0");
    }

    @Test
    void missingContextShouldRejectDatabaseQuery() {
        try (SqlSession session = sessionFactory.openSession(true)) {
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> session.getMapper(RecordMapper.class).selectList(new QueryWrapper<Record>()));
            Throwable root = failure;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void explicitlyMarkedPlatformTableShouldNotRequireTenantColumn() {
        try (SqlSession session = sessionFactory.openSession(true)) {
            assertThat(session.getMapper(PlatformMapper.class).selectCount(new QueryWrapper<PlatformRecord>()))
                    .isEqualTo(1L);
        }
    }

    @Test
    void insertWithoutTenantShouldReceiveCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(101L);
        Record record = new Record();
        record.setId(10L);
        record.setName("server-assigned-tenant");
        try (SqlSession session = sessionFactory.openSession(true)) {
            assertThat(session.getMapper(RecordMapper.class).insert(record)).isEqualTo(1);
        }
        assertThat(readColumn(10L, "tenant_id")).isEqualTo("101");
    }

    @Test
    @Tag("tenant-write-safety")
    void forgedInsertTenantShouldBeRejectedOrOverwrittenByTrustedTenant() throws Exception {
        TenantContextHolder.setTenantId(101L);
        Record record = new Record();
        record.setId(10L);
        record.setName("forged-tenant");
        record.setTenantId(202L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getMapper(RecordMapper.class).insert(record);
        } catch (RuntimeException rejection) {
            // 允许显式拒绝，但仍独立检查数据库，不能留下越租户数据。
            assertDeliberateRejection(rejection);
        }
        String storedTenant = readColumn(10L, "tenant_id");
        assertThat(storedTenant)
                .as("拒绝写入或归属当前可信租户，不能按伪造tenant_id写入")
                .isIn(null, "101");
    }

    @Test
    @Tag("tenant-write-safety")
    void updatingTenantColumnShouldNotTransferRecordToAnotherTenant() throws Exception {
        TenantContextHolder.setTenantId(101L);
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getMapper(RecordMapper.class).update(null,
                    new UpdateWrapper<Record>().eq("id", 1L).set("tenant_id", 202L));
        } catch (RuntimeException rejection) {
            // 可以拒绝整个更新，原行仍必须留在原租户。
            assertDeliberateRejection(rejection);
        }
        assertThat(readColumn(1L, "tenant_id"))
                .as("只增加WHERE租户条件不能授权修改归属字段")
                .isEqualTo("101");
    }

    private static void assertDeliberateRejection(RuntimeException failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        boolean constraintRejection = root instanceof SQLException
                && ((SQLException) root).getSQLState() != null
                && ((SQLException) root).getSQLState().startsWith("23");
        assertThat(root instanceof IllegalArgumentException
                || root instanceof IllegalStateException
                || root instanceof SecurityException
                || root instanceof ServiceException
                || constraintRejection)
                .as("只有明确的校验/权限/约束拒绝才可接受，SQL语法或连接故障不算保护成功")
                .isTrue();
    }

    private static String readColumn(long id, String trustedColumnExpression) throws Exception {
        try (Connection connection = checkedConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT " + trustedColumnExpression + " FROM " + RECORD_TABLE + " WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    public interface RecordMapper extends BaseMapperX<Record> {
    }

    public interface PlatformMapper extends BaseMapperX<PlatformRecord> {
    }

    @TableName(RECORD_TABLE)
    public static class Record extends TenantBaseDo {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @TenantIgnore
    @TableName(PLATFORM_TABLE)
    public static class PlatformRecord {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String name;
    }
}
