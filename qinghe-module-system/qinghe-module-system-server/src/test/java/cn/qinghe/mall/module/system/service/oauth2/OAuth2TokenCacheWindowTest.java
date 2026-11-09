package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.system.convert.oauth2.OAuth2TokenConvert;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.qinghe.mall.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.qinghe.mall.module.system.dal.redis.oauth2.dto.OAuth2AccessTokenCacheDTO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** DEC-076: reproduce cache age crossing during account verification, no external services. */
class OAuth2TokenCacheWindowTest {
    private final OAuth2AccessTokenMapper mapper = mock(OAuth2AccessTokenMapper.class);
    private final OAuth2AccessTokenRedisDAO redis = mock(OAuth2AccessTokenRedisDAO.class);
    private final OAuth2TokenEligibilityChecker eligibility = mock(OAuth2TokenEligibilityChecker.class);
    private final OAuth2TokenServiceImpl service = new OAuth2TokenServiceImpl(mapper, redis, eligibility);
    private static final String TOKEN = "synthetic-cache-window-token";

    @Test void cacheExpiringDuringEligibilityReadsAuthorityOnce() {
        OAuth2AccessTokenDO record = record();
        OAuth2AccessTokenCacheDTO cached = expiringCache(record);
        when(mapper.selectByAccessToken(TOKEN)).thenReturn(record);
        assertThat(service.checkAccessToken(TOKEN).getUserId()).isEqualTo(101L);
        verify(mapper, times(1)).selectByAccessToken(TOKEN);
        verify(eligibility, times(2)).check(anyLong(), anyInt(), anyLong(), anyString(), anyList(), anyMap());
        verify(redis).set(eq(TOKEN), argThat(value -> value != cached && value.getCheckedAt().isAfter(cached.getCheckedAt())));
    }

    @Test void reloadedRevocationIsRejectedInsteadOfAcceptingOldCache() {
        OAuth2AccessTokenDO record = record();
        expiringCache(record);
        record.setRevokedTime(LocalDateTime.now());
        when(mapper.selectByAccessToken(TOKEN)).thenReturn(record);
        assertThatThrownBy(() -> service.checkAccessToken(TOKEN)).isInstanceOf(ServiceException.class);
        verify(mapper, times(1)).selectByAccessToken(TOKEN);
        verify(redis, never()).set(anyString(), any());
    }

    @Test void unavailableEligibilityIsNotRetriedOrCached() {
        when(redis.get(TOKEN)).thenReturn(OAuth2TokenConvert.toCacheDTO(record(), LocalDateTime.now()));
        doThrow(new IllegalStateException("synthetic qualification unavailable")).when(eligibility)
                .check(anyLong(), anyInt(), anyLong(), anyString(), anyList(), anyMap());
        assertThatThrownBy(() -> service.checkAccessToken(TOKEN)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mapper);
        verify(redis, never()).set(anyString(), any());
        verify(eligibility, times(1)).check(anyLong(), anyInt(), anyLong(), anyString(), anyList(), anyMap());
    }

    @Test void freshHitKeepsCacheAndDoesNotQueryDatabase() {
        when(redis.get(TOKEN)).thenReturn(OAuth2TokenConvert.toCacheDTO(record(), LocalDateTime.now()));
        assertThat(service.checkAccessToken(TOKEN).getUserId()).isEqualTo(101L);
        verifyNoInteractions(mapper);
        verify(redis, never()).set(anyString(), any());
    }

    private OAuth2AccessTokenCacheDTO expiringCache(OAuth2AccessTokenDO record) {
        OAuth2AccessTokenCacheDTO cached = OAuth2TokenConvert.toCacheDTO(record, LocalDateTime.now().minusSeconds(4));
        when(redis.get(TOKEN)).thenReturn(cached);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) Thread.sleep(1100);
            return null;
        }).when(eligibility).check(anyLong(), anyInt(), anyLong(), anyString(), anyList(), anyMap());
        return cached;
    }

    private OAuth2AccessTokenDO record() {
        OAuth2AccessTokenDO value = new OAuth2AccessTokenDO();
        value.setUserId(101L); value.setUserType(UserTypeEnum.ADMIN.getValue()); value.setTenantId(72000001L);
        value.setClientId("qinghe-admin"); value.setScopes(Collections.emptyList()); value.setUserInfo(Collections.emptyMap());
        value.setExpiresTime(LocalDateTime.now().plusMinutes(10)); value.setDeleted(false);
        return value;
    }
}
