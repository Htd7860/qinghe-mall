package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2ClientMapper;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantPackageMapper;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Real Spring transaction proxy/manager with mocked JDBC connections and Mappers.
 * No connection reaches MySQL. JDBC commit/rollback verification is not a data rollback test.
 */
@Tag("auth-issuance-offline")
@Execution(ExecutionMode.SAME_THREAD)
class AdminTokenIssueServiceImplTest {

    private Fixture fixture;
    private AnnotationConfigApplicationContext context;
    private AdminTokenIssueService service;

    @BeforeAll
    static void prepareOnlyLambdaColumnMetadata() {
        // Resolve lambda column names for wrapper assertions; no SqlSessionFactory or JDBC.
        for (Class<?> entity : Arrays.asList(OAuth2ClientDO.class, TenantDO.class,
                TenantPackageDO.class, AdminUserDO.class)) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                        new MybatisConfiguration(), "offline-issuance-metadata");
                assistant.setCurrentNamespace(entity.getName() + ".offlineIssueTest");
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContextHolder.clear();
        fixture = new Fixture();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(Fixture.class, () -> fixture);
        context.register(OfflineConfiguration.class);
        context.refresh();
        service = context.getBean(AdminTokenIssueService.class);
    }

    @AfterEach
    void tearDown() {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void merchantSuccessShouldSaveThePairAndCommitBeforeReturning() throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        when(fixture.users.validateUserForAuthentication(101L, 10L)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            ConnectionHolder holder = (ConnectionHolder)
                    TransactionSynchronizationManager.getResource(fixture.dataSource);
            assertThat(holder).isNotNull();
            assertThat(holder.hasTimeout()).isTrue();
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(10L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            return fixture.current;
        });

        OAuth2TokenPairDTO result = issue();

        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(999L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();

        OAuth2RefreshTokenDO refresh = fixture.refreshRow;
        OAuth2AccessTokenDO access = fixture.accessRow;
        assertThat(refresh).isNotNull();
        assertThat(access).isNotNull();
        assertThat(result.getAccessToken()).matches("at_[A-Za-z0-9_-]{43}");
        assertThat(result.getRefreshToken()).matches("rt_[A-Za-z0-9_-]{43}");
        assertThat(access.getAccessToken()).isEqualTo(result.getAccessToken());
        assertThat(refresh.getRefreshToken()).isEqualTo(result.getRefreshToken());
        assertThat(access.getRefreshToken()).isEqualTo(refresh.getRefreshToken());
        assertThat(access.getUserId()).isEqualTo(101L);
        assertThat(refresh.getUserId()).isEqualTo(101L);
        assertThat(access.getUserType()).isEqualTo(2);
        assertThat(refresh.getUserType()).isEqualTo(2);
        assertThat(access.getTenantId()).isEqualTo(10L);
        assertThat(refresh.getTenantId()).isEqualTo(10L);
        assertThat(access.getClientId()).isEqualTo("qinghe-admin");
        assertThat(refresh.getClientId()).isEqualTo("qinghe-admin");
        assertThat(access.getScopes()).containsExactly("read");
        assertThat(refresh.getScopes()).containsExactly("read");
        assertThat(access.getUserInfo()).hasSize(2)
                .containsEntry("nickname", "Fixture Admin")
                .containsEntry("deptId", "7").doesNotContainKey("password");
        assertThat(access.getCreator()).isEqualTo("admin:101");
        assertThat(refresh.getCreator()).isEqualTo("admin:101");
        assertThat(access.getUpdater()).isEqualTo("admin:101");
        assertThat(refresh.getUpdater()).isEqualTo("admin:101");
        assertThat(access.getDeleted()).isFalse();
        assertThat(refresh.getDeleted()).isFalse();
        assertThat(access.getRevokedTime()).isNull();
        assertThat(refresh.getRevokedTime()).isNull();
        assertThat(access.getCreateTime()).isEqualTo(refresh.getCreateTime());
        assertThat(access.getUpdateTime()).isEqualTo(access.getCreateTime());
        assertThat(refresh.getUpdateTime()).isEqualTo(refresh.getCreateTime());
        assertThat(access.getCreateTime().getNano() % 1000).isZero();
        assertThat(Duration.between(access.getCreateTime(), result.getExpiresTime()).getSeconds())
                .isEqualTo(900L);
        assertThat(Duration.between(refresh.getCreateTime(), result.getRefreshExpiresTime()).getSeconds())
                .isEqualTo(604800L);
        assertThat(access.getExpiresTime()).isEqualTo(result.getExpiresTime());
        assertThat(refresh.getExpiresTime()).isEqualTo(result.getRefreshExpiresTime());
        assertThat(result.toString()).doesNotContain(result.getAccessToken(), result.getRefreshToken());

        InOrder order = inOrder(fixture.clients, fixture.tenants, fixture.packages,
                fixture.accounts, fixture.users, fixture.tenantService, fixture.clientService,
                fixture.refreshTokens, fixture.accessTokens);
        order.verify(fixture.clients).selectOne(any());
        order.verify(fixture.tenants).selectOne(any());
        order.verify(fixture.packages).selectOne(any());
        order.verify(fixture.accounts).selectOne(any());
        order.verify(fixture.users).validateUserForAuthentication(101L, 10L);
        order.verify(fixture.tenantService).validateForAuthentication(10L);
        order.verify(fixture.clientService).validateClient("qinghe-admin", 2, 10L, fixture.scopes);
        order.verify(fixture.refreshTokens).insert(any(OAuth2RefreshTokenDO.class));
        order.verify(fixture.accessTokens).insert(any(OAuth2AccessTokenDO.class));
        verify(fixture.tenantService, never()).validateForOperation(anyLong());
    }

    @Test
    void lockingWrappersShouldContainFixedLocksAndExactIdentityConditions() {
        issue();
        assertWrapper(fixture.clients, "client_id", "qinghe-admin");

        ArgumentCaptor<Wrapper<AdminUserDO>> accountQuery = wrapperCaptor();
        verify(fixture.accounts).selectOne(accountQuery.capture());
        LambdaQueryWrapper<AdminUserDO> query = (LambdaQueryWrapper<AdminUserDO>) accountQuery.getValue();
        assertThat(query.getSqlSegment()).contains("FOR SHARE", "tenant_id", "id");
        assertThat(query.getParamNameValuePairs().values()).contains(101L, 10L);

        ArgumentCaptor<Wrapper<TenantDO>> tenantQuery = wrapperCaptor();
        verify(fixture.tenants).selectOne(tenantQuery.capture());
        assertThat(tenantQuery.getValue().getSqlSegment()).contains("FOR SHARE", "id");
        ArgumentCaptor<Wrapper<TenantPackageDO>> packageQuery = wrapperCaptor();
        verify(fixture.packages).selectOne(packageQuery.capture());
        assertThat(packageQuery.getValue().getSqlSegment()).contains("FOR SHARE", "id");
        // This checks query construction only, not parser/plugin execution or actual locks.
    }

    @Test
    void platformShouldIssueForTenantZeroWithoutMerchantRows() {
        fixture.authenticated.setTenantId(0L);
        fixture.current.setTenantId(0L);
        fixture.client.setClientId("qinghe-platform");
        when(fixture.users.validateUserForAuthentication(101L, 0L)).thenReturn(fixture.current);
        when(fixture.clientService.validateClient("qinghe-platform", 2, 0L, fixture.scopes))
                .thenReturn(fixture.client);

        service.createToken(fixture.authenticated, "qinghe-platform", fixture.scopes);

        assertThat(fixture.accessRow.getTenantId()).isZero();
        assertThat(fixture.refreshRow.getTenantId()).isZero();
        verifyNoInteractions(fixture.tenants, fixture.packages, fixture.tenantService);
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void scopesShouldBeCopiedBeforeOtherServicesCanModifyTheCallersList() {
        List<String> original = new ArrayList<>(fixture.scopes);
        when(fixture.clientService.validateClient("qinghe-admin", 2, 10L, fixture.scopes))
                .thenAnswer(call -> {
                    List<String> received = call.getArgument(3);
                    assertThatThrownBy(() -> received.add("write"))
                            .isInstanceOf(UnsupportedOperationException.class);
                    original.clear();
                    return fixture.client;
                });

        service.createToken(fixture.authenticated, "qinghe-admin", original);

        assertThat(original).isEmpty();
        assertThat(fixture.refreshRow.getScopes()).containsExactly("read");
        assertThat(fixture.accessRow.getScopes()).containsExactly("read");
        assertThat(fixture.refreshRow.getScopes()).isNotSameAs(fixture.accessRow.getScopes());
    }

    enum MissingRow { CLIENT, TENANT, PACKAGE, ACCOUNT }

    @ParameterizedTest
    @EnumSource(MissingRow.class)
    void missingQualificationRowShouldRejectBeforeAnyInsert(MissingRow missing) throws Exception {
        switch (missing) {
            case CLIENT: when(fixture.clients.selectOne(any())).thenReturn(null); break;
            case TENANT: when(fixture.tenants.selectOne(any())).thenReturn(null); break;
            case PACKAGE: when(fixture.packages.selectOne(any())).thenReturn(null); break;
            case ACCOUNT: when(fixture.accounts.selectOne(any())).thenReturn(null); break;
            default: throw new AssertionError(missing);
        }
        assertThatThrownBy(this::issue).isInstanceOf(
                missing == MissingRow.PACKAGE ? IllegalStateException.class : ServiceException.class);
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens);
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
    }

    @Test
    void passwordChangeShouldRequireFreshAuthentication() throws Exception {
        fixture.current.setPassword("changed-fixture-hash");
        assertThatThrownBy(this::issue).isInstanceOf(ServiceException.class);
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens);
        verify(fixture.connection).rollback();
    }

    @Test
    void versionChangeWithoutPasswordChangeShouldAlsoReject() {
        fixture.current.setAuthVersion(4L);
        assertThatThrownBy(this::issue).isInstanceOf(ServiceException.class);
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens);
    }

    @Test
    void eligibilityServiceFailureShouldPropagateWithoutWriting() {
        IllegalStateException unavailable = new IllegalStateException("fixture qualification unavailable");
        when(fixture.clientService.validateClient("qinghe-admin", 2, 10L, fixture.scopes))
                .thenThrow(unavailable);
        assertThatThrownBy(this::issue).isSameAs(unavailable);
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens);
    }

    @Test
    void secondInsertFailureShouldRequestRollbackAndRestoreContextWithoutLeakingSqlCause()
            throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        doThrow(new DataIntegrityViolationException("duplicate token: fixture-secret-value"))
                .when(fixture.accessTokens).insert(any(OAuth2AccessTokenDO.class));

        assertThatThrownBy(this::issue).isInstanceOf(IllegalStateException.class)
                .hasMessage("令牌持久化失败，请重新登录").hasNoCause();

        verify(fixture.refreshTokens).insert(any(OAuth2RefreshTokenDO.class));
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(999L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
        // No real rows exist here: actual two-row rollback is still a MySQL integration test.
    }

    @Test
    void missingGeneratedRefreshIdShouldAbortBeforeTheAccessInsert() throws Exception {
        doReturn(1).when(fixture.refreshTokens).insert(any(OAuth2RefreshTokenDO.class));
        assertThatThrownBy(this::issue).isInstanceOf(IllegalStateException.class)
                .hasMessage("刷新令牌保存结果异常");
        verifyNoInteractions(fixture.accessTokens);
        verify(fixture.connection).rollback();
    }

    @Test
    void zeroAffectedAccessRowsShouldRejectAndRequestRollback() throws Exception {
        doReturn(0).when(fixture.accessTokens).insert(any(OAuth2AccessTokenDO.class));
        assertThatThrownBy(this::issue).isInstanceOf(IllegalStateException.class)
                .hasMessage("访问令牌保存结果异常");
        verify(fixture.connection).rollback();
    }

    @Test
    void commitFailureShouldThrowInsteadOfReturningAPair() throws Exception {
        doThrow(new SQLException("fixture commit response failure")).when(fixture.connection).commit();
        assertThatThrownBy(this::issue).isInstanceOf(TransactionSystemException.class);
        verify(fixture.connection).commit();
        // Commit outcome is unknown; do not assert that a real server rolled anything back.
    }

    @Test
    void manualTargetCallWithoutATransactionShouldRejectBeforeWriting() {
        assertThatThrownBy(() -> fixture.target.createToken(
                fixture.authenticated, "qinghe-admin", fixture.scopes))
                .isInstanceOf(IllegalStateException.class).hasMessage("令牌签发需要可写事务");
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens);
    }

    @Test
    void manualTargetCallWithinAReadOnlyTransactionShouldReject() {
        TransactionTemplate readOnly = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> fixture.target.createToken(
                fixture.authenticated, "qinghe-admin", fixture.scopes)))
                .isInstanceOf(IllegalStateException.class).hasMessage("令牌签发需要可写事务");
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens);
    }

    @Test
    void requiresNewShouldCommitItsConnectionIndependentlyOfOuterRollback() throws Exception {
        Connection outerConnection = connectionDouble();
        when(fixture.dataSource.getConnection()).thenReturn(outerConnection, fixture.connection);
        TransactionTemplate outer = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));

        outer.execute(status -> {
            OAuth2TokenPairDTO pair = issue();
            assertThat(pair).isNotNull();
            try {
                verify(fixture.connection).commit();
                verify(outerConnection, never()).commit();
            } catch (SQLException ex) {
                throw new AssertionError(ex);
            }
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            status.setRollbackOnly();
            return null;
        });

        verify(outerConnection).rollback();
        verify(fixture.connection, never()).rollback();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    enum BadInput { NULL_USER, NULL_ID, NEGATIVE_ID, NULL_TENANT, NEGATIVE_TENANT,
        EMPTY_PASSWORD, NULL_VERSION, NEGATIVE_VERSION, NULL_SCOPES, BAD_CLIENT }

    @ParameterizedTest
    @EnumSource(BadInput.class)
    void malformedInternalInputsShouldNotReachQualificationQueries(BadInput bad) {
        AdminUserDO user = fixture.authenticated;
        List<String> scopes = fixture.scopes;
        String client = "qinghe-admin";
        switch (bad) {
            case NULL_USER: user = null; break;
            case NULL_ID: user.setId(null); break;
            case NEGATIVE_ID: user.setId(-1L); break;
            case NULL_TENANT: user.setTenantId(null); break;
            case NEGATIVE_TENANT: user.setTenantId(-1L); break;
            case EMPTY_PASSWORD: user.setPassword(""); break;
            case NULL_VERSION: user.setAuthVersion(null); break;
            case NEGATIVE_VERSION: user.setAuthVersion(-1L); break;
            case NULL_SCOPES: scopes = null; break;
            case BAD_CLIENT: client = "bad client"; break;
            default: throw new AssertionError(bad);
        }
        AdminUserDO inputUser = user;
        List<String> inputScopes = scopes;
        String inputClient = client;
        assertThatThrownBy(() -> service.createToken(inputUser, inputClient, inputScopes))
                .isInstanceOf(bad == BadInput.BAD_CLIENT ? ServiceException.class : IllegalStateException.class);
        verifyNoInteractions(fixture.clients, fixture.tenants, fixture.packages, fixture.accounts,
                fixture.users, fixture.tenantService, fixture.clientService,
                fixture.refreshTokens, fixture.accessTokens);
    }

    private OAuth2TokenPairDTO issue() {
        return service.createToken(fixture.authenticated, "qinghe-admin", fixture.scopes);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private void assertWrapper(OAuth2ClientMapper mapper, String column, String value) {
        ArgumentCaptor<Wrapper<OAuth2ClientDO>> captor = wrapperCaptor();
        verify(mapper).selectOne(captor.capture());
        LambdaQueryWrapper<OAuth2ClientDO> query = (LambdaQueryWrapper<OAuth2ClientDO>) captor.getValue();
        assertThat(query.getSqlSegment()).contains("FOR SHARE", column);
        assertThat(query.getParamNameValuePairs().values()).contains(value);
    }

    private static Connection connectionDouble() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        return connection;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = false)
    static class OfflineConfiguration {
        @Bean
        DataSource dataSource(Fixture fixture) {
            return fixture.dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        AdminTokenIssueService issuer(Fixture fixture) {
            return fixture.target;
        }
    }

    static class Fixture {
        final AdminUserMapper accounts = mock(AdminUserMapper.class);
        final TenantMapper tenants = mock(TenantMapper.class);
        final TenantPackageMapper packages = mock(TenantPackageMapper.class);
        final OAuth2ClientMapper clients = mock(OAuth2ClientMapper.class);
        final OAuth2RefreshTokenMapper refreshTokens = mock(OAuth2RefreshTokenMapper.class);
        final OAuth2AccessTokenMapper accessTokens = mock(OAuth2AccessTokenMapper.class);
        final AdminUserService users = mock(AdminUserService.class);
        final TenantService tenantService = mock(TenantService.class);
        final OAuth2ClientService clientService = mock(OAuth2ClientService.class);
        final OAuth2AccessTokenRedisDAO cache = mock(OAuth2AccessTokenRedisDAO.class);
        final DataSource dataSource = mock(DataSource.class);
        final Connection connection;
        final AdminTokenIssueServiceImpl target;
        final AdminUserDO authenticated = account();
        final AdminUserDO current = account();
        final OAuth2ClientDO client = new OAuth2ClientDO();
        final List<String> scopes = Collections.singletonList("read");
        OAuth2RefreshTokenDO refreshRow;
        OAuth2AccessTokenDO accessRow;

        Fixture() throws Exception {
            connection = connectionDouble();
            when(dataSource.getConnection()).thenReturn(connection);
            client.setClientId("qinghe-admin");
            client.setAccessTokenValiditySeconds(900);
            client.setRefreshTokenValiditySeconds(604800);
            TenantDO tenant = new TenantDO();
            tenant.setId(10L);
            tenant.setPackageId(30L);
            when(clients.selectOne(any())).thenReturn(client);
            when(tenants.selectOne(any())).thenReturn(tenant);
            when(packages.selectOne(any())).thenReturn(new TenantPackageDO());
            when(accounts.selectOne(any())).thenReturn(current);
            when(users.validateUserForAuthentication(101L, 10L)).thenReturn(current);
            when(clientService.validateClient("qinghe-admin", 2, 10L, scopes)).thenReturn(client);
            when(refreshTokens.insert(any(OAuth2RefreshTokenDO.class))).thenAnswer(call -> {
                refreshRow = call.getArgument(0);
                refreshRow.setId(11L);
                return 1;
            });
            when(accessTokens.insert(any(OAuth2AccessTokenDO.class))).thenAnswer(call -> {
                accessRow = call.getArgument(0);
                accessRow.setId(22L);
                return 1;
            });
            target = new AdminTokenIssueServiceImpl(accounts, tenants, packages, clients,
                    refreshTokens, accessTokens, users, tenantService, clientService,
                    cache);
        }

        private static AdminUserDO account() {
            AdminUserDO user = new AdminUserDO();
            user.setId(101L);
            user.setTenantId(10L);
            user.setPassword("fixture-hash");
            user.setAuthVersion(3L);
            user.setNickname("Fixture Admin");
            user.setDeptId(7L);
            return user;
        }
    }
}
