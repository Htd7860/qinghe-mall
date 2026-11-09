package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.OAUTH2_REFRESH_TOKEN_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Real Spring transaction proxies with mock JDBC, Mappers and Redis DAO only.
 * No real SQL, locks, rollback, Redis TTL or concurrent database behavior is exercised.
 */
@Tag("token-lifecycle-offline")
@Execution(ExecutionMode.SAME_THREAD)
class OAuth2TokenLifecycleTest {

    private static final String REFRESH = "rt_offline_session";
    private static final String FIRST = "at_offline_first";
    private static final String SECOND = "at_offline_second";
    private static final String CLIENT = "qinghe-admin";

    private AdminTokenIssueServiceImplTest.Fixture fixture;
    private AnnotationConfigApplicationContext context;
    private AdminTokenIssueService issuer;
    private OAuth2TokenRevokeService revoker;
    private OAuth2TokenRevokeServiceImpl revokeTarget;
    private OAuth2RefreshTokenDO parent;
    private OAuth2AccessTokenDO first;
    private OAuth2AccessTokenDO second;

    @BeforeAll
    static void prepareLambdaMetadataOnly() {
        AdminTokenIssueServiceImplTest.prepareOnlyLambdaColumnMetadata();
        for (Class<?> entity : Arrays.asList(OAuth2AccessTokenDO.class, OAuth2RefreshTokenDO.class)) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                        new MybatisConfiguration(), "offline-token-lifecycle-metadata");
                assistant.setCurrentNamespace(entity.getName() + ".offlineLifecycleTest");
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContextHolder.clear();
        // Reuse the existing offline issuance fixture rather than build another test framework.
        fixture = new AdminTokenIssueServiceImplTest.Fixture();
        parent = parent();
        first = access(21L, FIRST);
        second = access(22L, SECOND);
        when(fixture.refreshTokens.selectByRefreshTokenForUpdate(REFRESH)).thenReturn(parent);
        when(fixture.accessTokens.selectByAccessToken(FIRST)).thenReturn(first);
        when(fixture.accessTokens.selectListByRefreshTokenForUpdate(REFRESH))
                .thenReturn(Arrays.asList(first, second));
        when(fixture.accessTokens.update(isNull(), any())).thenReturn(2);
        when(fixture.refreshTokens.update(isNull(), any())).thenReturn(1);
        when(fixture.accessTokens.insert(any(OAuth2AccessTokenDO.class))).thenAnswer(call -> {
            fixture.accessRow = call.getArgument(0);
            fixture.accessRow.setId(31L);
            return 1;
        });
        revokeTarget = new OAuth2TokenRevokeServiceImpl(
                fixture.accessTokens, fixture.refreshTokens, fixture.cache);

        context = new AnnotationConfigApplicationContext();
        context.registerBean(AdminTokenIssueServiceImplTest.Fixture.class, () -> fixture);
        context.registerBean(OAuth2TokenRevokeServiceImpl.class, () -> revokeTarget);
        context.register(AdminTokenIssueServiceImplTest.OfflineConfiguration.class);
        context.refresh();
        issuer = context.getBean(AdminTokenIssueService.class);
        revoker = context.getBean(OAuth2TokenRevokeService.class);
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
    void refreshShouldCommitNewAccessBeforeDeletingOldCache() throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        when(fixture.accessTokens.selectListByRefreshTokenForUpdate(REFRESH)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(10L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            verifyNoInteractions(fixture.cache);
            return Arrays.asList(first, second);
        });

        OAuth2TokenPairDTO result = refresh();

        assertThat(AopUtils.isAopProxy(issuer)).isTrue();
        assertThat(result.getAccessToken()).matches("at_[A-Za-z0-9_-]{43}");
        assertThat(result.getAccessToken()).isNotEqualTo(FIRST).isNotEqualTo(SECOND);
        assertThat(result.getRefreshToken()).isEqualTo(REFRESH);
        assertThat(result.getRefreshExpiresTime()).isEqualTo(parent.getExpiresTime());
        assertThat(parent.getRevokedTime()).isNull();
        assertThat(fixture.accessRow.getTenantId()).isEqualTo(10L);
        assertThat(fixture.accessRow.getUserId()).isEqualTo(101L);
        assertThat(fixture.accessRow.getUserType()).isEqualTo(2);
        assertThat(fixture.accessRow.getClientId()).isEqualTo(CLIENT);
        assertThat(fixture.accessRow.getRefreshToken()).isEqualTo(REFRESH);
        assertThat(fixture.accessRow.getScopes()).containsExactly("read");
        assertThat(fixture.accessRow.getScopes()).isNotSameAs(parent.getScopes());
        assertThat(fixture.accessRow.getUserInfo()).containsEntry("nickname", "Fixture Admin")
                .containsEntry("deptId", "7").doesNotContainKey("password");
        assertThat(fixture.accessRow.getCreator()).isEqualTo("admin:101");
        assertThat(fixture.accessRow.getDeleted()).isFalse();
        assertThat(fixture.accessRow.getRevokedTime()).isNull();
        assertThat(Duration.between(fixture.accessRow.getCreateTime(), result.getExpiresTime()).getSeconds())
                .isEqualTo(900L);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(999L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        InOrder order = inOrder(fixture.refreshTokens, fixture.clients,
                fixture.accessTokens, fixture.connection, fixture.cache);
        order.verify(fixture.refreshTokens).selectByRefreshTokenForUpdate(REFRESH);
        order.verify(fixture.clients).selectOne(any());
        order.verify(fixture.accessTokens).selectListByRefreshTokenForUpdate(REFRESH);
        order.verify(fixture.accessTokens).update(isNull(), any());
        order.verify(fixture.accessTokens).insert(any(OAuth2AccessTokenDO.class));
        order.verify(fixture.connection).commit();
        order.verify(fixture.cache).delete(FIRST);
        order.verify(fixture.cache).delete(SECOND);
        verify(fixture.connection, never()).rollback();
        verify(fixture.refreshTokens, never()).insert(any(OAuth2RefreshTokenDO.class));
        verify(fixture.refreshTokens, never()).update(isNull(), any());
        assertAccessUpdate("token-refresh");
    }

    @Test
    void refreshShouldCapAccessExpiryAtUnchangedParentExpiry() {
        LocalDateTime absoluteExpiry = LocalDateTime.now().plusMinutes(5);
        parent.setExpiresTime(absoluteExpiry);

        OAuth2TokenPairDTO result = refresh();

        assertThat(result.getExpiresTime()).isEqualTo(absoluteExpiry);
        assertThat(result.getRefreshExpiresTime()).isEqualTo(absoluteExpiry);
        assertThat(parent.getExpiresTime()).isEqualTo(absoluteExpiry);
    }

    enum InvalidParent { MISSING, DELETED, REVOKED, EXPIRED, WRONG_CLIENT, MEMBER, NULL_ID, NULL_EXPIRY }

    @ParameterizedTest
    @EnumSource(InvalidParent.class)
    void invalidRefreshParentShouldRejectBeforeAccessMutation(InvalidParent bad) throws Exception {
        switch (bad) {
            case MISSING: when(fixture.refreshTokens.selectByRefreshTokenForUpdate(REFRESH)).thenReturn(null); break;
            case DELETED: parent.setDeleted(true); break;
            case REVOKED: parent.setRevokedTime(LocalDateTime.now().minusMinutes(1)); break;
            case EXPIRED: parent.setExpiresTime(LocalDateTime.now().minusSeconds(1)); break;
            case WRONG_CLIENT: parent.setClientId("other-client"); break;
            case MEMBER: parent.setUserType(1); break;
            case NULL_ID: parent.setId(null); break;
            case NULL_EXPIRY: parent.setExpiresTime(null); break;
            default: throw new AssertionError(bad);
        }
        if (bad == InvalidParent.NULL_ID || bad == InvalidParent.NULL_EXPIRY) {
            assertThatThrownBy(this::refresh).isInstanceOf(IllegalStateException.class);
        } else {
            assertRefreshRejected();
        }
        verifyNoInteractions(fixture.accessTokens, fixture.cache);
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
    }

    enum BadInput { NULL_TOKEN, EMPTY_TOKEN, SPACE_TOKEN, COMMA_TOKEN, TOO_LONG, BAD_CLIENT }

    @ParameterizedTest
    @EnumSource(BadInput.class)
    void malformedRefreshInputShouldNotQueryAnyIdentity(BadInput bad) {
        String token = REFRESH;
        String client = CLIENT;
        switch (bad) {
            case NULL_TOKEN: token = null; break;
            case EMPTY_TOKEN: token = ""; break;
            case SPACE_TOKEN: token = "rt_bad value"; break;
            case COMMA_TOKEN: token = "rt_one,rt_two"; break;
            case TOO_LONG: token = String.join("", Collections.nCopies(129, "a")); break;
            case BAD_CLIENT: client = "bad client"; break;
            default: throw new AssertionError(bad);
        }
        String inputToken = token;
        String inputClient = client;
        assertThatThrownBy(() -> issuer.refreshAccessToken(inputToken, inputClient))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens, fixture.clients,
                fixture.users, fixture.tenantService, fixture.clientService, fixture.cache);
    }

    @Test
    void refreshShouldNormalizeKnownEligibilityFailureOnly() throws Exception {
        when(fixture.users.validateUserForAuthentication(101L, 10L))
                .thenThrow(new ServiceException(401, "fixture disabled account"));

        assertRefreshRejected();

        verifyNoInteractions(fixture.accessTokens, fixture.cache);
        verify(fixture.connection).rollback();
    }

    @Test
    void refreshShouldPreserveInfrastructureFaultAndRestoreTenant() throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        IllegalStateException unavailable = new IllegalStateException("fixture unavailable");
        when(fixture.users.validateUserForAuthentication(101L, 10L)).thenThrow(unavailable);

        assertThatThrownBy(this::refresh).isSameAs(unavailable);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(999L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
        verifyNoInteractions(fixture.accessTokens, fixture.cache);
        verify(fixture.connection).rollback();
    }

    @Test
    void refreshShouldRejectExpiryReachedDuringQualification() throws Exception {
        when(fixture.users.validateUserForAuthentication(101L, 10L)).thenAnswer(call -> {
            // Exercise a time-boundary branch deterministically; this does not simulate a DB lock wait.
            parent.setExpiresTime(LocalDateTime.now().minusSeconds(1));
            return fixture.current;
        });

        assertRefreshRejected();

        verifyNoInteractions(fixture.accessTokens, fixture.cache);
        verify(fixture.connection).rollback();
    }

    @Test
    void refreshShouldRejectMismatchedChildWithoutWriting() throws Exception {
        second.setTenantId(20L);

        assertThatThrownBy(this::refresh).isInstanceOf(IllegalStateException.class)
                .hasMessage("访问令牌关联异常");

        verify(fixture.accessTokens, never()).update(isNull(), any());
        verify(fixture.accessTokens, never()).insert(any(OAuth2AccessTokenDO.class));
        assertRollbackWithoutCache();
    }

    @Test
    void refreshShouldRejectBulkAffectedCountMismatch() throws Exception {
        when(fixture.accessTokens.update(isNull(), any())).thenReturn(1);

        assertThatThrownBy(this::refresh).isInstanceOf(IllegalStateException.class)
                .hasMessage("旧访问令牌撤销结果异常");

        verify(fixture.accessTokens, never()).insert(any(OAuth2AccessTokenDO.class));
        assertRollbackWithoutCache();
    }

    @Test
    void refreshInsertFailureShouldRollbackAndNeverRunRegisteredCacheCallback() throws Exception {
        when(fixture.accessTokens.insert(any(OAuth2AccessTokenDO.class)))
                .thenThrow(new DataIntegrityViolationException("fixture SQL with secret"));

        assertThatThrownBy(this::refresh).isInstanceOf(IllegalStateException.class)
                .hasMessage("刷新令牌持久化失败，请重试").hasNoCause();

        verify(fixture.accessTokens).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    @Test
    void refreshCommitFailureShouldNotRunAfterCommitCacheDeletion() throws Exception {
        doThrow(new SQLException("fixture commit failure")).when(fixture.connection).commit();

        assertThatThrownBy(this::refresh).isInstanceOf(TransactionSystemException.class);

        verifyNoInteractions(fixture.cache);
        verify(fixture.connection).commit();
        // A commit exception has uncertain real-world outcome; do not assert real data rollback here.
    }

    @Test
    void refreshCacheFailureShouldTryAllKeysAndReportAlreadyCommitted() throws Exception {
        doThrow(new IllegalStateException("fixture Redis unavailable"))
                .when(fixture.cache).delete(FIRST);

        assertThatThrownBy(this::refresh).isInstanceOf(IllegalStateException.class)
                .hasMessage("令牌已刷新，旧缓存清理未完成，请重试刷新");

        InOrder order = inOrder(fixture.connection, fixture.cache);
        order.verify(fixture.connection).commit();
        order.verify(fixture.cache).delete(FIRST);
        order.verify(fixture.cache).delete(SECOND);
        verify(fixture.connection, never()).rollback();
    }

    @Test
    void refreshShouldUseIndependentTransactionInsideOuterRollback() throws Exception {
        Connection outerConnection = connectionDouble();
        when(fixture.dataSource.getConnection()).thenReturn(outerConnection, fixture.connection);
        TransactionTemplate outer = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));

        outer.execute(status -> {
            assertThat(refresh()).isNotNull();
            verify(fixture.cache).delete(FIRST);
            status.setRollbackOnly();
            return null;
        });

        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(outerConnection).rollback();
        verify(outerConnection, never()).commit();
    }

    @Test
    void directRefreshCallWithoutProxyTransactionShouldReject() {
        assertThatThrownBy(() -> fixture.target.refreshAccessToken(REFRESH, CLIENT))
                .isInstanceOf(IllegalStateException.class).hasMessage("刷新令牌需要可写事务");
        verifyNoInteractions(fixture.refreshTokens, fixture.accessTokens, fixture.cache);
    }

    @Test
    void refreshShouldHandleSessionWithoutPreviousAccessRows() {
        when(fixture.accessTokens.selectListByRefreshTokenForUpdate(REFRESH))
                .thenReturn(Collections.emptyList());

        assertThat(refresh().getRefreshToken()).isEqualTo(REFRESH);

        verify(fixture.accessTokens, never()).update(isNull(), any());
        verify(fixture.accessTokens).insert(any(OAuth2AccessTokenDO.class));
        verifyNoInteractions(fixture.cache);
    }

    @Test
    void refreshShouldRejectExpiryReachedAfterChildLockAndNotInsert() throws Exception {
        when(fixture.accessTokens.selectListByRefreshTokenForUpdate(REFRESH)).thenAnswer(call -> {
            parent.setExpiresTime(LocalDateTime.now().minusSeconds(1));
            return Arrays.asList(first, second);
        });

        assertRefreshRejected();

        verify(fixture.accessTokens).update(isNull(), any());
        verify(fixture.accessTokens, never()).insert(any(OAuth2AccessTokenDO.class));
        assertRollbackWithoutCache();
    }

    @Test
    void refreshShouldPreserveClientScopeRejectionWithoutWriting() throws Exception {
        when(fixture.clientService.validateClient(CLIENT, 2, 10L, fixture.scopes))
                .thenThrow(new ServiceException(401, "fixture scope no longer allowed"));

        assertRefreshRejected();

        verifyNoInteractions(fixture.accessTokens, fixture.cache);
        verify(fixture.connection).rollback();
    }

    @Test
    void refreshShouldNotReturnCredentialWhenInsertCountIsZero() throws Exception {
        when(fixture.accessTokens.insert(any(OAuth2AccessTokenDO.class))).thenReturn(0);

        assertThatThrownBy(this::refresh).isInstanceOf(IllegalStateException.class)
                .hasMessage("访问令牌保存结果异常");

        assertRollbackWithoutCache();
    }

    @Test
    void platformRefreshShouldNotRequireMerchantTenantOrPackage() {
        parent.setTenantId(0L);
        first.setTenantId(0L);
        second.setTenantId(0L);
        fixture.current.setTenantId(0L);
        when(fixture.users.validateUserForAuthentication(101L, 0L)).thenReturn(fixture.current);
        when(fixture.clientService.validateClient(CLIENT, 2, 0L, fixture.scopes))
                .thenReturn(fixture.client);

        assertThat(refresh().getAccessToken()).isNotEmpty();

        assertThat(fixture.accessRow.getTenantId()).isZero();
        verifyNoInteractions(fixture.tenants, fixture.packages, fixture.tenantService);
    }

    @Test
    void readOnlyDirectTargetsShouldRejectRefreshAndRevoke() {
        TransactionTemplate readOnly = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        readOnly.setReadOnly(true);

        assertThatThrownBy(() -> readOnly.execute(status ->
                fixture.target.refreshAccessToken(REFRESH, CLIENT)))
                .isInstanceOf(IllegalStateException.class).hasMessage("刷新令牌需要可写事务");
        assertThatThrownBy(() -> readOnly.execute(status -> {
            revokeTarget.revokeAccessToken(FIRST, CLIENT);
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessage("撤销令牌需要可写事务");

        verifyNoInteractions(fixture.accessTokens, fixture.refreshTokens, fixture.cache);
    }

    @Test
    void refreshShouldOnlyCountUnrevokedRowsButDeleteAllOldKeys() {
        LocalDateTime originalRevokedAt = LocalDateTime.now().minusHours(1);
        second.setRevokedTime(originalRevokedAt);
        when(fixture.accessTokens.update(isNull(), any())).thenReturn(1);

        assertThat(refresh().getAccessToken()).isNotEmpty();

        assertThat(second.getRevokedTime()).isEqualTo(originalRevokedAt);
        verify(fixture.cache).delete(FIRST);
        verify(fixture.cache).delete(SECOND);
        assertAccessUpdate("token-refresh");
    }

    @Test
    void revokeShouldLockParentBeforeChildrenAndDeleteCacheAfterCommit() throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        when(fixture.accessTokens.selectListByRefreshTokenForUpdate(REFRESH)).thenAnswer(call -> {
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(10L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            verifyNoInteractions(fixture.cache);
            return Arrays.asList(first, second);
        });

        revoke();

        assertThat(AopUtils.isAopProxy(revoker)).isTrue();
        InOrder order = inOrder(fixture.accessTokens, fixture.refreshTokens,
                fixture.connection, fixture.cache);
        order.verify(fixture.accessTokens).selectByAccessToken(FIRST);
        order.verify(fixture.refreshTokens).selectByRefreshTokenForUpdate(REFRESH);
        order.verify(fixture.accessTokens).selectListByRefreshTokenForUpdate(REFRESH);
        order.verify(fixture.refreshTokens).update(isNull(), any());
        order.verify(fixture.accessTokens).update(isNull(), any());
        order.verify(fixture.connection).commit();
        order.verify(fixture.cache).delete(FIRST);
        order.verify(fixture.cache).delete(SECOND);
        verify(fixture.connection, never()).rollback();
        verifyNoInteractions(fixture.users, fixture.tenantService, fixture.clientService);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(999L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
        assertAccessUpdate("token-revoke");

        ArgumentCaptor<Wrapper<OAuth2RefreshTokenDO>> captor = wrapperCaptor();
        verify(fixture.refreshTokens).update(isNull(), captor.capture());
        LambdaUpdateWrapper<OAuth2RefreshTokenDO> update =
                (LambdaUpdateWrapper<OAuth2RefreshTokenDO>) captor.getValue();
        assertThat(update.getSqlSegment()).contains("id", "tenant_id", "revoked_time", "IS NULL");
        assertThat(update.getSqlSet()).contains("revoked_time", "updater", "update_time")
                .doesNotContain("tenant_id", "user_id", "client_id", "refresh_token", "deleted");
        assertThat(update.getParamNameValuePairs().values()).contains(11L, 10L, "token-revoke");
    }

    @Test
    void repeatedRevokeShouldPreserveFirstTimeAndStillDeleteBothCacheKeys() throws Exception {
        LocalDateTime revokedAt = LocalDateTime.now().minusHours(1);
        parent.setRevokedTime(revokedAt);
        first.setRevokedTime(revokedAt);
        second.setRevokedTime(revokedAt);

        revoke();
        revoke();

        verify(fixture.refreshTokens, never()).update(isNull(), any());
        verify(fixture.accessTokens, never()).update(isNull(), any());
        verify(fixture.cache, times(2)).delete(FIRST);
        verify(fixture.cache, times(2)).delete(SECOND);
        verify(fixture.connection, times(2)).commit();
        assertThat(parent.getRevokedTime()).isEqualTo(revokedAt);
        assertThat(first.getRevokedTime()).isEqualTo(revokedAt);
    }

    @Test
    void revokeUnknownAccessShouldOnlyDeleteThatCacheKeyAfterCommit() throws Exception {
        when(fixture.accessTokens.selectByAccessToken(FIRST)).thenReturn(null);

        revoke();

        verifyNoInteractions(fixture.refreshTokens);
        verify(fixture.accessTokens, never()).update(isNull(), any());
        InOrder order = inOrder(fixture.connection, fixture.cache);
        order.verify(fixture.connection).commit();
        order.verify(fixture.cache).delete(FIRST);
        verify(fixture.cache, never()).delete(SECOND);
    }

    @Test
    void revokeShouldAllowExpiredSessionWithoutCallingEligibilityServices() {
        first.setExpiresTime(LocalDateTime.now().minusDays(1));
        parent.setExpiresTime(LocalDateTime.now().minusDays(1));

        revoke();

        verify(fixture.refreshTokens).update(isNull(), any());
        verify(fixture.accessTokens).update(isNull(), any());
        verifyNoInteractions(fixture.users, fixture.tenants, fixture.packages,
                fixture.clients, fixture.tenantService, fixture.clientService);
    }

    @Test
    void revokeWrongClientShouldRejectBeforeParentLookup() throws Exception {
        first.setClientId("other-client");

        assertThatThrownBy(this::revoke).isInstanceOf(ServiceException.class);

        verifyNoInteractions(fixture.refreshTokens);
        verify(fixture.accessTokens, never()).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    @Test
    void revokeMissingParentShouldReportFaultInsteadOfPretendingSuccess() throws Exception {
        when(fixture.refreshTokens.selectByRefreshTokenForUpdate(REFRESH)).thenReturn(null);

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("刷新令牌关联异常");

        verify(fixture.accessTokens, never()).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    enum ParentMismatch { TENANT, USER, USER_TYPE, CLIENT }

    @ParameterizedTest
    @EnumSource(ParentMismatch.class)
    void revokeMismatchedParentShouldNeverWrite(ParentMismatch bad) throws Exception {
        switch (bad) {
            case TENANT: parent.setTenantId(20L); break;
            case USER: parent.setUserId(202L); break;
            case USER_TYPE: parent.setUserType(1); break;
            case CLIENT: parent.setClientId("other-client"); break;
            default: throw new AssertionError(bad);
        }

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("刷新令牌关联异常");

        verify(fixture.refreshTokens, never()).update(isNull(), any());
        verify(fixture.accessTokens, never()).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    @Test
    void revokeShouldRejectChildrenMissingTheLocatedAccess() throws Exception {
        when(fixture.accessTokens.selectListByRefreshTokenForUpdate(REFRESH))
                .thenReturn(Collections.singletonList(second));

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("访问令牌关联异常");

        verify(fixture.refreshTokens, never()).update(isNull(), any());
        verify(fixture.accessTokens, never()).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    @Test
    void revokeParentUpdateMismatchShouldNotMutateChildren() throws Exception {
        when(fixture.refreshTokens.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("刷新令牌撤销结果异常");

        verify(fixture.accessTokens, never()).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    @Test
    void revokeChildWriteFailureShouldRollbackAndNotDeleteCache() throws Exception {
        when(fixture.accessTokens.update(isNull(), any()))
                .thenThrow(new DataIntegrityViolationException("fixture SQL containing credential"));

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("令牌撤销持久化失败，请重试").hasNoCause();

        verify(fixture.refreshTokens).update(isNull(), any());
        assertRollbackWithoutCache();
    }

    @Test
    void revokeCacheFailureShouldBeRetryableAfterCommittedRevocation() throws Exception {
        doThrow(new IllegalStateException("fixture Redis unavailable"))
                .when(fixture.cache).delete(FIRST);

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("令牌已持久撤销，缓存清理未完成，请重试");
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.cache).delete(SECOND);

        // Next read represents persisted revocation. These are mock snapshots, not a real DB.
        LocalDateTime persistedRevokedAt = LocalDateTime.now().minusSeconds(1);
        parent.setRevokedTime(persistedRevokedAt);
        first.setRevokedTime(persistedRevokedAt);
        second.setRevokedTime(persistedRevokedAt);
        doNothing().when(fixture.cache).delete(FIRST);

        revoke();

        verify(fixture.connection, times(2)).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.refreshTokens, times(1)).update(isNull(), any());
        verify(fixture.accessTokens, times(1)).update(isNull(), any());
        verify(fixture.cache, times(2)).delete(FIRST);
        verify(fixture.cache, times(2)).delete(SECOND);
        assertThat(parent.getRevokedTime()).isEqualTo(persistedRevokedAt);
    }

    @Test
    void revokeCommitFailureShouldNotDeleteCache() throws Exception {
        doThrow(new SQLException("fixture commit failure")).when(fixture.connection).commit();

        assertThatThrownBy(this::revoke).isInstanceOf(TransactionSystemException.class);

        verifyNoInteractions(fixture.cache);
    }

    @Test
    void revokeFailureShouldRestoreOuterTenantContext() throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        when(fixture.accessTokens.update(isNull(), any())).thenReturn(1);

        assertThatThrownBy(this::revoke).isInstanceOf(IllegalStateException.class)
                .hasMessage("访问令牌撤销结果异常");

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(999L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
        assertRollbackWithoutCache();
    }

    @Test
    void directRevokeWithoutTransactionShouldReject() {
        assertThatThrownBy(() -> revokeTarget.revokeAccessToken(FIRST, CLIENT))
                .isInstanceOf(IllegalStateException.class).hasMessage("撤销令牌需要可写事务");
        verifyNoInteractions(fixture.accessTokens, fixture.refreshTokens, fixture.cache);
    }

    @Test
    void revokeMalformedInputShouldNotQueryRecords() {
        assertThatThrownBy(() -> revoker.revokeAccessToken("at_bad value", CLIENT))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> revoker.revokeAccessToken(FIRST, "bad client"))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(fixture.accessTokens, fixture.refreshTokens, fixture.cache);
    }

    private OAuth2TokenPairDTO refresh() {
        return issuer.refreshAccessToken(REFRESH, CLIENT);
    }

    private void revoke() {
        revoker.revokeAccessToken(FIRST, CLIENT);
    }

    private void assertRefreshRejected() {
        assertThatThrownBy(this::refresh).isInstanceOfSatisfying(ServiceException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(OAUTH2_REFRESH_TOKEN_INVALID.getCode());
            assertThat(ex.getMessage()).isEqualTo(OAUTH2_REFRESH_TOKEN_INVALID.getMsg());
        });
    }

    private void assertRollbackWithoutCache() throws SQLException {
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
        verifyNoInteractions(fixture.cache);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private void assertAccessUpdate(String updater) {
        ArgumentCaptor<Wrapper<OAuth2AccessTokenDO>> captor = wrapperCaptor();
        verify(fixture.accessTokens).update(isNull(), captor.capture());
        LambdaUpdateWrapper<OAuth2AccessTokenDO> update =
                (LambdaUpdateWrapper<OAuth2AccessTokenDO>) captor.getValue();
        assertThat(update.getSqlSegment()).contains("refresh_token", "tenant_id", "revoked_time", "IS NULL");
        assertThat(update.getSqlSet()).contains("revoked_time", "updater", "update_time")
                .doesNotContain("tenant_id", "user_id", "client_id", "refresh_token", "deleted");
        assertThat(update.getParamNameValuePairs().values()).contains(REFRESH, 10L, updater);
    }

    private static OAuth2RefreshTokenDO parent() {
        OAuth2RefreshTokenDO refresh = new OAuth2RefreshTokenDO();
        refresh.setId(11L);
        refresh.setRefreshToken(REFRESH);
        refresh.setTenantId(10L);
        refresh.setUserId(101L);
        refresh.setUserType(2);
        refresh.setClientId(CLIENT);
        refresh.setScopes(Collections.singletonList("read"));
        refresh.setExpiresTime(LocalDateTime.now().plusDays(7));
        refresh.setDeleted(false);
        return refresh;
    }

    private static OAuth2AccessTokenDO access(Long id, String value) {
        OAuth2AccessTokenDO access = new OAuth2AccessTokenDO();
        access.setId(id);
        access.setAccessToken(value);
        access.setRefreshToken(REFRESH);
        access.setTenantId(10L);
        access.setUserId(101L);
        access.setUserType(2);
        access.setClientId(CLIENT);
        access.setExpiresTime(LocalDateTime.now().plusMinutes(15));
        access.setDeleted(false);
        return access;
    }

    private static Connection connectionDouble() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        return connection;
    }
}
