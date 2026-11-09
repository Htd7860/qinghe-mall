package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.module.system.convert.oauth2.OAuth2TokenConvert;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.qinghe.mall.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.qinghe.mall.module.system.dal.redis.oauth2.dto.OAuth2AccessTokenCacheDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.*;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2TokenServiceImpl
 * @Date：2026/10/24 10:41
 * @Filename：OAuth2TokenServiceImpl
 */
@Service
public class OAuth2TokenServiceImpl implements OAuth2TokenService{

    private final OAuth2AccessTokenMapper accessTokenMapper;
    private final OAuth2AccessTokenRedisDAO accessTokenRedisDAO;
    private final OAuth2TokenEligibilityChecker eligibilityChecker;

    public OAuth2TokenServiceImpl(
            OAuth2AccessTokenMapper accessTokenMapper,
            OAuth2AccessTokenRedisDAO accessTokenRedisDAO,
            OAuth2TokenEligibilityChecker eligibilityChecker) {
        this.accessTokenMapper = Objects.requireNonNull(accessTokenMapper);
        this.accessTokenRedisDAO = Objects.requireNonNull(accessTokenRedisDAO);
        this.eligibilityChecker = Objects.requireNonNull(eligibilityChecker);
    }

    @Override
    public OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken) {
        validateAccessToken(accessToken);

        for (int attempt = 0; attempt < 2; attempt++) {
            OAuth2AccessTokenCacheDTO snapshot = attempt == 0 ? accessTokenRedisDAO.get(accessToken) : null;
            boolean cacheMiss = snapshot == null;

            if(cacheMiss){
                LocalDateTime checkedAt = LocalDateTime.now();

                OAuth2AccessTokenDO record =
                        accessTokenMapper.selectByAccessToken(accessToken);
                if (record == null || Boolean.TRUE.equals(record.getDeleted())) {
                    throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
                }
                snapshot = OAuth2TokenConvert.toCacheDTO(record, checkedAt);
            }

            validateSnapshot(snapshot);

            eligibilityChecker.check(
                    snapshot.getUserId(),
                    snapshot.getUserType(),
                    snapshot.getTenantId(),
                    snapshot.getClientId(),
                    Collections.unmodifiableList(
                            new ArrayList<>(snapshot.getScopes())),
                    Collections.unmodifiableMap(new java.util.HashMap<>(snapshot.getUserInfo())));

            validateSnapshot(snapshot);

            if (isSnapshotStale(snapshot)) {
                // A cache hit may expire while checking the current account. Read authority once;
                // never extend checkedAt on the cached object or relax the five-second boundary.
                if (!cacheMiss) {
                    continue;
                }
                throw new IllegalStateException("令牌状态已超过有效检查窗口，请重试");
            }

            if (cacheMiss) {
                accessTokenRedisDAO.set(accessToken, snapshot);
            }

            validateSnapshot(snapshot);
            if (isSnapshotStale(snapshot)) {
                if (!cacheMiss) {
                    continue;
                }
                throw new IllegalStateException("令牌状态已超过有效检查窗口，请重试");
            }

            return OAuth2TokenConvert.toCheckRespDTO(snapshot);
        }
        throw new IllegalStateException("令牌状态已超过有效检查窗口，请重试");
    }

    private void validateAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken) || accessToken.length() > 128
                || accessToken.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch)
                || Character.isISOControl(ch)
                || ch == ',')) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }
    }

    private void validateSnapshot(OAuth2AccessTokenCacheDTO snapshot) {
        if (snapshot == null
                || snapshot.getUserId() == null || snapshot.getUserId() <= 0
                || snapshot.getUserType() == null
                || UserTypeEnum.valueOf(snapshot.getUserType()) == null
                || snapshot.getTenantId() == null || snapshot.getTenantId() < 0
                || !StringUtils.hasText(snapshot.getClientId())
                || snapshot.getUserInfo() == null || snapshot.getScopes() == null
                || snapshot.getExpiresTime() == null
                || snapshot.getCheckedAt() == null) {
            throw new IllegalStateException("令牌身份数据不完整");
        }

        LocalDateTime now = LocalDateTime.now();

        if (snapshot.getCheckedAt().isAfter(now)) {
            throw new IllegalStateException("令牌检查时间晚于当前时间");
        }

        if (snapshot.getRevokedTime() != null) {
            throw exception(OAUTH2_ACCESS_TOKEN_REVOKED);
        }

        if (!snapshot.getExpiresTime().isAfter(now)) {
            throw exception(OAUTH2_ACCESS_TOKEN_EXPIRED);
        }

    }

    private boolean isSnapshotStale(OAuth2AccessTokenCacheDTO snapshot) {
        return !snapshot.getCheckedAt().plusSeconds(5).isAfter(LocalDateTime.now());
    }
}
