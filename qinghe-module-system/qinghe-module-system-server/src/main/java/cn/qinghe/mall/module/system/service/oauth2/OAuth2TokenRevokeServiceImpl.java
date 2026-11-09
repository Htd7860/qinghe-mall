package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.qinghe.mall.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.OAUTH2_ACCESS_TOKEN_INVALID;

@Service
@RequiredArgsConstructor
public class OAuth2TokenRevokeServiceImpl implements OAuth2TokenRevokeService {

    private final OAuth2AccessTokenMapper accessTokenMapper;
    private final OAuth2RefreshTokenMapper refreshTokenMapper;
    private final OAuth2AccessTokenRedisDAO accessTokenRedisDAO;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class, timeout = 10)
    public void revokeAccessToken(String accessToken, String clientId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("撤销令牌需要可写事务");
        }
        if (!validCredential(accessToken)
                || clientId == null || !clientId.matches("[A-Za-z0-9:_-]{1,64}")) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }
        try {
            // 先定位，随后按父刷新记录→关联访问记录的顺序加锁。
            OAuth2AccessTokenDO access = accessTokenMapper.selectByAccessToken(accessToken);
            if (access == null) {
                deleteCacheAfterCommit(Collections.singletonList(accessToken));
                return;
            }
            if (!clientId.equals(access.getClientId())) {
                throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
            }
            if (access.getId() == null || access.getId() <= 0
                    || !accessToken.equals(access.getAccessToken())
                    || access.getTenantId() == null || access.getTenantId() < 0
                    || access.getUserId() == null || access.getUserId() <= 0
                    || UserTypeEnum.valueOf(access.getUserType()) == null
                    || !validCredential(access.getRefreshToken())) {
                throw new IllegalStateException("访问令牌身份数据异常");
            }
            OAuth2RefreshTokenDO refresh =
                    refreshTokenMapper.selectByRefreshTokenForUpdate(access.getRefreshToken());
            if (refresh == null || refresh.getId() == null || refresh.getId() <= 0
                    || !sameSession(access, refresh)) {
                throw new IllegalStateException("刷新令牌关联异常");
            }
            TenantUtils.execute(refresh.getTenantId(), () -> {
                revokeSession(access, refresh);
                return null;
            });
        } catch (DataAccessException ex) {
            // 不向上附带可能含原始Token的SQL异常消息或cause。
            throw new IllegalStateException("令牌撤销持久化失败，请重试");
        }
    }

    private void revokeSession(OAuth2AccessTokenDO target, OAuth2RefreshTokenDO refresh) {
        List<OAuth2AccessTokenDO> accesses =
                accessTokenMapper.selectListByRefreshTokenForUpdate(refresh.getRefreshToken());
        if (accesses == null
                || accesses.stream().anyMatch(access -> access == null || !sameSession(access, refresh))
                || accesses.stream().noneMatch(access -> target.getId().equals(access.getId())
                        && target.getAccessToken().equals(access.getAccessToken()))) {
            throw new IllegalStateException("访问令牌关联异常");
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (refresh.getRevokedTime() == null) {
            int changed = refreshTokenMapper.update(null,
                    new LambdaUpdateWrapper<OAuth2RefreshTokenDO>()
                            .eq(OAuth2RefreshTokenDO::getId, refresh.getId())
                            .eq(OAuth2RefreshTokenDO::getTenantId, refresh.getTenantId())
                            .isNull(OAuth2RefreshTokenDO::getRevokedTime)
                            .set(OAuth2RefreshTokenDO::getRevokedTime, now)
                            .set(OAuth2RefreshTokenDO::getUpdater, "token-revoke")
                            .set(OAuth2RefreshTokenDO::getUpdateTime, now));
            if (changed != 1) {
                throw new IllegalStateException("刷新令牌撤销结果异常");
            }
        }
        long expected = accesses.stream().filter(access -> access.getRevokedTime() == null).count();
        if (expected > 0) {
            // 同一会话的访问记录一次更新，不逐条发UPDATE。
            int changed = accessTokenMapper.update(null,
                    new LambdaUpdateWrapper<OAuth2AccessTokenDO>()
                            .eq(OAuth2AccessTokenDO::getRefreshToken, refresh.getRefreshToken())
                            .eq(OAuth2AccessTokenDO::getTenantId, refresh.getTenantId())
                            .isNull(OAuth2AccessTokenDO::getRevokedTime)
                            .set(OAuth2AccessTokenDO::getRevokedTime, now)
                            .set(OAuth2AccessTokenDO::getUpdater, "token-revoke")
                            .set(OAuth2AccessTokenDO::getUpdateTime, now));
            if (changed != expected) {
                throw new IllegalStateException("访问令牌撤销结果异常");
            }
        }
        deleteCacheAfterCommit(accesses.stream().map(OAuth2AccessTokenDO::getAccessToken)
                .collect(Collectors.toList()));
    }

    private void deleteCacheAfterCommit(List<String> accessTokens) {
        List<String> values = new ArrayList<>(accessTokens);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean failed = false;
                for (String value : values) {
                    try {
                        accessTokenRedisDAO.delete(value);
                    } catch (RuntimeException ex) {
                        failed = true;
                    }
                }
                if (failed) {
                    // 数据库已经提交，不能将缓存错误描述为数据库回滚。
                    throw new IllegalStateException("令牌已持久撤销，缓存清理未完成，请重试");
                }
            }
        });
    }

    private boolean sameSession(OAuth2AccessTokenDO access, OAuth2RefreshTokenDO refresh) {
        return !Boolean.TRUE.equals(access.getDeleted())
                && !Boolean.TRUE.equals(refresh.getDeleted())
                && Objects.equals(access.getRefreshToken(), refresh.getRefreshToken())
                && Objects.equals(access.getTenantId(), refresh.getTenantId())
                && Objects.equals(access.getUserId(), refresh.getUserId())
                && Objects.equals(access.getUserType(), refresh.getUserType())
                && Objects.equals(access.getClientId(), refresh.getClientId());
    }

    private boolean validCredential(String value) {
        return StringUtils.hasText(value) && value.length() <= 128
                && value.chars().noneMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch) || Character.isISOControl(ch) || ch == ',');
    }
}
