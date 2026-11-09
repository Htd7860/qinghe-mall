package cn.qinghe.mall.module.system.service.user;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Offline service contracts; no Spring Boot, database, Redis or Nacos. */
@Tag("auth-issuance-offline")
class AdminUserServiceImplTest {

    private AdminUserMapper mapper;
    private PasswordEncoder encoder;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminUserMapper.class);
        // Lower cost only for test speed; production encoder is unchanged.
        encoder = new BCryptPasswordEncoder(4);
        service = new AdminUserServiceImpl(mapper, encoder);
    }

    @Test
    void passwordMatchingShouldUseTheStoredHashAndPreserveWhitespace() {
        String encoded = encoder.encode("  FixturePassword  ");

        assertThat(service.isPasswordMatch("  FixturePassword  ", encoded)).isTrue();
        assertThat(service.isPasswordMatch("FixturePassword", encoded)).isFalse();
        assertThat(service.isPasswordMatch("wrong", encoded)).isFalse();
    }

    @Test
    void passwordLimitShouldUseUtf8BytesAndAcceptTheBoundary() {
        String ascii72 = String.join("", Collections.nCopies(72, "a"));
        String chinese72Bytes = String.join("", Collections.nCopies(24, "密"));

        assertThat(service.isPasswordMatch(ascii72, encoder.encode(ascii72))).isTrue();
        assertThat(service.isPasswordMatch(chinese72Bytes, encoder.encode(chinese72Bytes))).isTrue();

        PasswordEncoder guardedEncoder = mock(PasswordEncoder.class);
        AdminUserServiceImpl guarded = new AdminUserServiceImpl(mapper, guardedEncoder);
        assertThat(guarded.isPasswordMatch(ascii72 + "b", "fixture-hash")).isFalse();
        assertThat(guarded.isPasswordMatch(chinese72Bytes + "密", "fixture-hash")).isFalse();
        assertThat(guarded.isPasswordMatch(null, "fixture-hash")).isFalse();
        assertThat(guarded.isPasswordMatch("", "fixture-hash")).isFalse();
        verifyNoInteractions(guardedEncoder);
    }

    @Test
    void missingStoredPasswordShouldBeAServiceFault() {
        assertThatThrownBy(() -> service.isPasswordMatch("valid", null))
                .isInstanceOf(IllegalStateException.class).hasMessage("账号密码配置异常");
        assertThatThrownBy(() -> service.isPasswordMatch("valid", ""))
                .isInstanceOf(IllegalStateException.class).hasMessage("账号密码配置异常");
    }

    @Test
    void enabledAccountShouldBeReturnedForItsExactIdentity() {
        AdminUserDO account = account();
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(account);

        assertThat(service.validateUserForAuthentication(101L, 10L)).isSameAs(account);
    }

    @Test
    void missingDeletedOrDisabledAccountsShouldBeUnauthorized() {
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(null);
        assertUnauthorized();

        AdminUserDO deleted = account();
        deleted.setDeleted(true);
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(deleted);
        assertUnauthorized();

        AdminUserDO disabled = account();
        disabled.setStatus(1);
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(disabled);
        assertUnauthorized();
    }

    @Test
    void wrongIdentityUnknownStatusAndBadVersionShouldBeServiceFaults() {
        AdminUserDO wrongTenant = account();
        wrongTenant.setTenantId(20L);
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(wrongTenant);
        assertThatThrownBy(() -> service.validateUserForAuthentication(101L, 10L))
                .isInstanceOf(IllegalStateException.class);

        AdminUserDO unknownStatus = account();
        unknownStatus.setStatus(9);
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(unknownStatus);
        assertThatThrownBy(() -> service.validateUserForAuthentication(101L, 10L))
                .isInstanceOf(IllegalStateException.class);

        AdminUserDO badVersion = account();
        badVersion.setAuthVersion(-1L);
        when(mapper.selectByIdAndTenant(101L, 10L)).thenReturn(badVersion);
        assertThatThrownBy(() -> service.validateUserForAuthentication(101L, 10L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void databaseFailureShouldRemainAServiceFailure() {
        IllegalStateException failure = new IllegalStateException("fixture database unavailable");
        when(mapper.selectByIdAndTenant(101L, 10L)).thenThrow(failure);

        assertThatThrownBy(() -> service.validateUserForAuthentication(101L, 10L))
                .isSameAs(failure);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(() -> service.validateUserForAuthentication(101L, 10L))
                .isInstanceOfSatisfying(ServiceException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(401));
    }

    private AdminUserDO account() {
        AdminUserDO account = new AdminUserDO();
        account.setId(101L);
        account.setTenantId(10L);
        account.setStatus(0);
        account.setAuthVersion(3L);
        account.setDeleted(false);
        return account;
    }
}
