package cn.qinghe.mall.module.system.service.auth;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.service.oauth2.AdminTokenIssueService;
import cn.qinghe.mall.module.system.service.oauth2.OAuth2TokenRevokeService;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;

import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Internal auth orchestration only; all dependent services are test doubles. */
@Tag("auth-issuance-offline")
class AdminAuthServiceImplTest {

    private AdminUserService users;
    private TenantService tenants;
    private AdminTokenIssueService issuer;
    private OAuth2TokenRevokeService revoker;
    private AdminAuthServiceImpl service;
    private AdminUserDO account;
    private final List<String> scopes = Collections.singletonList("read");

    @BeforeEach
    void setUp() {
        users = mock(AdminUserService.class);
        tenants = mock(TenantService.class);
        issuer = mock(AdminTokenIssueService.class);
        revoker = mock(OAuth2TokenRevokeService.class);
        service = new AdminAuthServiceImpl(users, tenants, issuer, revoker);
        account = account(10L);
    }

    @Test
    void merchantLoginShouldAuthenticateBeforeIssuingTokens() {
        stubSuccessfulAuthentication(10L);
        OAuth2TokenPairDTO pair = new OAuth2TokenPairDTO();
        when(issuer.createToken(account, "qinghe-admin", scopes)).thenReturn(pair);

        assertThat(login()).isSameAs(pair);

        InOrder order = inOrder(users, tenants, issuer);
        order.verify(users).getUserByUsername("admin1", 10L);
        order.verify(users).isPasswordMatch("fixture-password", "fixture-hash");
        order.verify(users).validateUserForAuthentication(101L, 10L);
        order.verify(tenants).validateForAuthentication(10L);
        order.verify(issuer).createToken(account, "qinghe-admin", scopes);
        verify(tenants, never()).validateForOperation(anyLong());
    }

    @Test
    void platformLoginShouldUseItsRealAccountWithoutAMerchantTenant() {
        account = account(0L);
        stubSuccessfulAuthentication(0L);
        OAuth2TokenPairDTO pair = new OAuth2TokenPairDTO();
        when(issuer.createToken(account, "qinghe-platform", scopes)).thenReturn(pair);

        assertThat(service.login("admin1", "fixture-password", 0L,
                "qinghe-platform", scopes)).isSameAs(pair);
        verify(users).validateUserForAuthentication(101L, 0L);
        verifyNoInteractions(tenants);
    }

    @Test
    void missingOrDeletedAccountShouldNotReachPasswordCheckingOrIssuance() {
        assertLoginRejected();
        account.setDeleted(true);
        when(users.getUserByUsername("admin1", 10L)).thenReturn(account);
        assertLoginRejected();
        verify(users, never()).isPasswordMatch("fixture-password", "fixture-hash");
        verifyNoInteractions(tenants, issuer);
    }

    @Test
    void wrongPasswordShouldNotReachQualificationOrIssuance() {
        when(users.getUserByUsername("admin1", 10L)).thenReturn(account);
        when(users.isPasswordMatch("fixture-password", "fixture-hash")).thenReturn(false);

        assertLoginRejected();
        verify(users, never()).validateUserForAuthentication(anyLong(), anyLong());
        verifyNoInteractions(tenants, issuer);
    }

    @Test
    void passwordChangeDuringVerificationShouldRejectLogin() {
        stubSuccessfulAuthentication(10L);
        AdminUserDO changed = account(10L);
        changed.setPassword("changed-fixture-hash");
        when(users.validateUserForAuthentication(101L, 10L)).thenReturn(changed);

        assertLoginRejected();
        verifyNoInteractions(tenants, issuer);
    }

    @Test
    void accountOrTenantEligibilityFailureShouldUseUniformLoginError() {
        stubSuccessfulAuthentication(10L);
        when(users.validateUserForAuthentication(101L, 10L))
                .thenThrow(new ServiceException(401, "fixture account disabled"));
        assertLoginRejected();
        verifyNoInteractions(issuer);

        doReturn(account).when(users).validateUserForAuthentication(101L, 10L);
        when(tenants.validateForAuthentication(10L))
                .thenThrow(new ServiceException(401, "fixture tenant deleted"));
        assertLoginRejected();
        verifyNoInteractions(issuer);
    }

    @Test
    void infrastructureAndNon401BusinessFailuresShouldNotBecomeBadCredentials() {
        stubSuccessfulAuthentication(10L);
        IllegalStateException unavailable = new IllegalStateException("fixture unavailable");
        when(users.validateUserForAuthentication(101L, 10L)).thenThrow(unavailable);
        assertThatThrownBy(this::login).isSameAs(unavailable);
        verifyNoInteractions(issuer);

        doReturn(account).when(users).validateUserForAuthentication(101L, 10L);
        ServiceException otherFailure = new ServiceException(503, "fixture unavailable");
        when(tenants.validateForAuthentication(10L)).thenThrow(otherFailure);
        assertThatThrownBy(this::login).isSameAs(otherFailure);
        verifyNoInteractions(issuer);
    }

    @Test
    void issuerFailureShouldPreserveServiceFaultsAndNormalizeOnly401() {
        stubSuccessfulAuthentication(10L);
        when(issuer.createToken(account, "qinghe-admin", scopes))
                .thenThrow(new ServiceException(401, "fixture identity changed"));
        assertLoginRejected();

        IllegalStateException persistenceFault = new IllegalStateException("fixture write failure");
        doThrow(persistenceFault).when(issuer).createToken(account, "qinghe-admin", scopes);
        assertThatThrownBy(this::login).isSameAs(persistenceFault);
    }

    @Test
    void invalidUsernameOrOversizedPasswordShouldNotQueryAccounts() {
        assertThatThrownBy(() -> service.login("bad name", "fixture", 10L,
                "qinghe-admin", scopes)).isInstanceOf(ServiceException.class);
        String oversized = String.join("", Collections.nCopies(25, "密"));
        assertThatThrownBy(() -> service.login("admin1", oversized, 10L,
                "qinghe-admin", scopes)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(users, tenants, issuer);
    }

    @Test
    void invalidTargetTenantShouldBeAnInternalContractFault() {
        assertThatThrownBy(() -> service.login("admin1", "fixture", null,
                "qinghe-admin", scopes)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.login("admin1", "fixture", -1L,
                "qinghe-admin", scopes)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users, tenants, issuer);
    }

    @Test
    void mismatchedAccountIdentityShouldBeAServiceFault() {
        account.setTenantId(20L);
        when(users.getUserByUsername("admin1", 10L)).thenReturn(account);
        assertThatThrownBy(this::login).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tenants, issuer);
    }

    @Test
    void refreshShouldDelegateWithoutPasswordAuthentication() {
        OAuth2TokenPairDTO pair = new OAuth2TokenPairDTO();
        when(issuer.refreshAccessToken("rt_fixture", "qinghe-admin")).thenReturn(pair);

        assertThat(service.refreshToken("rt_fixture", "qinghe-admin")).isSameAs(pair);

        verify(issuer).refreshAccessToken("rt_fixture", "qinghe-admin");
        verifyNoInteractions(users, tenants, revoker);
    }

    @Test
    void refreshShouldPreserveCredentialAndPostCommitFailures() {
        ServiceException rejected = new ServiceException(401, "fixture refresh rejected");
        when(issuer.refreshAccessToken("rt_fixture", "qinghe-admin")).thenThrow(rejected);
        assertThatThrownBy(() -> service.refreshToken("rt_fixture", "qinghe-admin"))
                .isSameAs(rejected);

        IllegalStateException cacheFault = new IllegalStateException("fixture already committed");
        doThrow(cacheFault).when(issuer).refreshAccessToken("rt_fixture", "qinghe-admin");
        assertThatThrownBy(() -> service.refreshToken("rt_fixture", "qinghe-admin"))
                .isSameAs(cacheFault);
        verifyNoInteractions(users, tenants, revoker);
    }

    @Test
    void logoutShouldDelegateWithoutFreshAccountEligibility() {
        service.logout("at_fixture", "qinghe-admin");

        verify(revoker).revokeAccessToken("at_fixture", "qinghe-admin");
        verifyNoInteractions(users, tenants, issuer);
    }

    @Test
    void logoutShouldNotHidePersistedRevocationCacheFailure() {
        IllegalStateException cacheFault = new IllegalStateException("fixture already revoked");
        doThrow(cacheFault).when(revoker).revokeAccessToken("at_fixture", "qinghe-admin");

        assertThatThrownBy(() -> service.logout("at_fixture", "qinghe-admin"))
                .isSameAs(cacheFault);
        verifyNoInteractions(users, tenants, issuer);
    }

    private void stubSuccessfulAuthentication(Long tenantId) {
        when(users.getUserByUsername("admin1", tenantId)).thenReturn(account);
        when(users.isPasswordMatch("fixture-password", "fixture-hash")).thenReturn(true);
        when(users.validateUserForAuthentication(101L, tenantId)).thenReturn(account);
    }

    private OAuth2TokenPairDTO login() {
        return service.login("admin1", "fixture-password", 10L, "qinghe-admin", scopes);
    }

    private void assertLoginRejected() {
        assertThatThrownBy(this::login).isInstanceOfSatisfying(ServiceException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(AUTH_LOGIN_BAD_CREDENTIALS.getCode());
            assertThat(ex.getMessage()).isEqualTo(AUTH_LOGIN_BAD_CREDENTIALS.getMsg());
        });
    }

    private AdminUserDO account(Long tenantId) {
        AdminUserDO user = new AdminUserDO();
        user.setId(101L);
        user.setUsername("admin1");
        user.setTenantId(tenantId);
        user.setPassword("fixture-hash");
        user.setAuthVersion(3L);
        user.setDeleted(false);
        return user;
    }
}
