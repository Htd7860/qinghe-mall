package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.OAUTH2_REFRESH_TOKEN_INVALID;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：AdminTokenIssueServiceImpl
 * @Date：2026/10/25 17:59
 * @Filename：AdminTokenIssueServiceImpl
 */

@Service
@RequiredArgsConstructor
public class AdminTokenIssueServiceImpl implements AdminTokenIssueService{

    private final AdminUserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final OAuth2ClientMapper clientMapper;
    private final OAuth2RefreshTokenMapper refreshTokenMapper;
    private final OAuth2AccessTokenMapper accessTokenMapper;
    private final AdminUserService userService;
    private final TenantService tenantService;
    private final OAuth2ClientService clientService;
    private final OAuth2AccessTokenRedisDAO accessTokenRedisDAO;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class, timeout = 10)
    public OAuth2TokenPairDTO createToken(AdminUserDO authenticatedUser, String clientId, List<String> scopes) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("令牌签发需要可写事务");
        }
        if (authenticatedUser == null || authenticatedUser.getId() == null
                || authenticatedUser.getId() <= 0
                || authenticatedUser.getTenantId() == null
                || authenticatedUser.getTenantId() < 0
                || authenticatedUser.getPassword() == null
                || authenticatedUser.getPassword().isEmpty()
                || authenticatedUser.getAuthVersion() == null
                || authenticatedUser.getAuthVersion() < 0 || scopes == null) {
            throw new IllegalStateException("签发所需认证结果不完整");
        }
        if (clientId == null || !clientId.matches("[A-Za-z0-9:_-]{1,64}")) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }

        // 只复制内部认证结果中本批需要的字段，不继续依赖可变DO。
        Long userId = authenticatedUser.getId();
        Long tenantId = authenticatedUser.getTenantId();
        String expectedPassword = authenticatedUser.getPassword();
        Long expectedVersion = authenticatedUser.getAuthVersion();
        List<String> requestedScopes = Collections.unmodifiableList(new ArrayList<>(scopes));

        return TenantUtils.execute(tenantId, () -> {
            lockQualificationRows(userId, tenantId, clientId);

            AdminUserDO current = userService.validateUserForAuthentication(userId, tenantId);
            if (!Objects.equals(expectedPassword, current.getPassword())
                    || !Objects.equals(expectedVersion, current.getAuthVersion())) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            if (tenantId > 0) {
                tenantService.validateForAuthentication(tenantId);
            }
            OAuth2ClientDO client = clientService.validateClient(
                    clientId, UserTypeEnum.ADMIN.getValue(), tenantId, requestedScopes);

            try {
                return persistPair(current, client, requestedScopes);
            } catch (DataAccessException ex) {
                // 不携带可能包含原始凭据的SQL异常消息或cause。
                throw new IllegalStateException("令牌持久化失败，请重新登录");
            }
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class, timeout = 10)
    public OAuth2TokenPairDTO refreshAccessToken(String refreshToken, String clientId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("刷新令牌需要可写事务");
        }
        if (!StringUtils.hasText(refreshToken) || refreshToken.length() > 128
                || refreshToken.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch) || Character.isISOControl(ch) || ch == ',')
                || clientId == null || !clientId.matches("[A-Za-z0-9:_-]{1,64}")) {
            throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
        }
        try {
            // 与退出使用同一父行锁：拿到锁后，才能判断这个会话是否仍可刷新。
            OAuth2RefreshTokenDO refresh = refreshTokenMapper.selectByRefreshTokenForUpdate(refreshToken);
            if (refresh == null || Boolean.TRUE.equals(refresh.getDeleted())
                    || !clientId.equals(refresh.getClientId())
                    || refresh.getRevokedTime() != null) {
                throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
            }
            if (refresh.getId() == null || refresh.getId() <= 0
                    || !refreshToken.equals(refresh.getRefreshToken())
                    || refresh.getUserId() == null || refresh.getUserId() <= 0
                    || refresh.getTenantId() == null || refresh.getTenantId() < 0
                    || refresh.getExpiresTime() == null || refresh.getScopes() == null) {
                throw new IllegalStateException("刷新令牌身份数据异常");
            }
            if (!UserTypeEnum.ADMIN.getValue().equals(refresh.getUserType())
                    || !refresh.getExpiresTime().isAfter(LocalDateTime.now())) {
                throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
            }
            return TenantUtils.execute(refresh.getTenantId(), () -> {
                lockQualificationRows(refresh.getUserId(), refresh.getTenantId(), clientId);
                AdminUserDO user = userService.validateUserForAuthentication(
                        refresh.getUserId(), refresh.getTenantId());
                if (!Objects.equals(refresh.getCredentialVersion(), AdminCredentialVersion.current(user))) {
                    throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
                }
                if (refresh.getTenantId() > 0) {
                    tenantService.validateForAuthentication(refresh.getTenantId());
                }
                OAuth2ClientDO client = clientService.validateClient(clientId,
                        refresh.getUserType(), refresh.getTenantId(), refresh.getScopes());
                // 加锁和资格查询可能等待，写入前重新检查绝对到期时间。
                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
                if (!refresh.getExpiresTime().isAfter(now)) {
                    throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
                }
                revokePreviousAccessTokens(refresh, now);
                LocalDateTime issuedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
                if (!refresh.getExpiresTime().isAfter(issuedAt)) {
                    throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
                }
                return persistAccessToken(user, client, refresh, issuedAt);
            });
        } catch (ServiceException ex) {
            if (GlobalErrorCodeConstants.UNAUTHORIZED.getCode().equals(ex.getCode())) {
                throw exception(OAUTH2_REFRESH_TOKEN_INVALID);
            }
            throw ex;
        } catch (DataAccessException ex) {
            throw new IllegalStateException("刷新令牌持久化失败，请重试");
        }
    }

    /** 以固定顺序锁住资格记录；锁一直持有到整个签发事务结束。 */
    private void lockQualificationRows(Long userId, Long tenantId, String clientId) {
        OAuth2ClientDO client = clientMapper.selectOne(new LambdaQueryWrapper<OAuth2ClientDO>()
                .eq(OAuth2ClientDO::getClientId, clientId).last("FOR SHARE"));
        if (client == null) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (tenantId > 0) {
            TenantDO tenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantDO>()
                    .eq(TenantDO::getId, tenantId).last("FOR SHARE"));
            if (tenant == null) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            if (tenant.getPackageId() == null || tenant.getPackageId() <= 0) {
                throw new IllegalStateException("租户套餐配置异常");
            }
            TenantPackageDO tenantPackage = packageMapper.selectOne(
                    new LambdaQueryWrapper<TenantPackageDO>()
                            .eq(TenantPackageDO::getId, tenant.getPackageId()).last("FOR SHARE"));
            if (tenantPackage == null) {
                throw new IllegalStateException("租户关联套餐配置异常");
            }
        }
        AdminUserDO user = userMapper.selectOne(new LambdaQueryWrapper<AdminUserDO>()
                .eq(AdminUserDO::getId, userId)
                .eq(AdminUserDO::getTenantId, tenantId).last("FOR SHARE"));
        if (user == null) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
    }

    /** 两条记录参与外层签发事务；不提前向Redis写入有效快照。 */
    private OAuth2TokenPairDTO persistPair(AdminUserDO user, OAuth2ClientDO client,
                                          List<String> scopes) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime refreshExpires = now.plusSeconds(client.getRefreshTokenValiditySeconds());
        String refreshValue = OAuth2TokenGenerator.generateRefreshToken();

        OAuth2RefreshTokenDO refresh = new OAuth2RefreshTokenDO();
        refresh.setRefreshToken(refreshValue);
        refresh.setUserId(user.getId());
        refresh.setUserType(UserTypeEnum.ADMIN.getValue());
        refresh.setCredentialVersion(AdminCredentialVersion.current(user));
        refresh.setClientId(client.getClientId());
        refresh.setScopes(new ArrayList<>(scopes));
        refresh.setExpiresTime(refreshExpires);
        fillAudit(refresh, user, now);
        if (refreshTokenMapper.insert(refresh) != 1
                || refresh.getId() == null || refresh.getId() <= 0) {
            throw new IllegalStateException("刷新令牌保存结果异常");
        }
        return persistAccessToken(user, client, refresh, now);
    }

    /** 登录与刷新共用保存逻辑；访问令牌不能超过所属刷新会话的到期时间。 */
    private OAuth2TokenPairDTO persistAccessToken(AdminUserDO user, OAuth2ClientDO client,
                                                  OAuth2RefreshTokenDO refresh, LocalDateTime now) {
        String accessValue = OAuth2TokenGenerator.generateAccessToken();
        LocalDateTime accessExpires = now.plusSeconds(client.getAccessTokenValiditySeconds());
        if (accessExpires.isAfter(refresh.getExpiresTime())) {
            accessExpires = refresh.getExpiresTime();
        }
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put(AdminCredentialVersion.INFO_KEY, AdminCredentialVersion.current(user).toString());
        if (user.getNickname() != null) {
            userInfo.put("nickname", user.getNickname());
        }
        if (user.getDeptId() != null) {
            userInfo.put("deptId", user.getDeptId().toString());
        }
        OAuth2AccessTokenDO access = new OAuth2AccessTokenDO();
        access.setAccessToken(accessValue);
        access.setRefreshToken(refresh.getRefreshToken());
        access.setUserId(user.getId());
        access.setUserType(UserTypeEnum.ADMIN.getValue());
        access.setUserInfo(userInfo);
        access.setClientId(client.getClientId());
        access.setScopes(new ArrayList<>(refresh.getScopes()));
        access.setExpiresTime(accessExpires);
        fillAudit(access, user, now);
        if (accessTokenMapper.insert(access) != 1
                || access.getId() == null || access.getId() <= 0) {
            throw new IllegalStateException("访问令牌保存结果异常");
        }

        OAuth2TokenPairDTO result = new OAuth2TokenPairDTO();
        result.setAccessToken(accessValue);
        result.setRefreshToken(refresh.getRefreshToken());
        result.setExpiresTime(accessExpires);
        result.setRefreshExpiresTime(refresh.getExpiresTime());
        return result;
    }

    /** 已锁父记录，再锁关联访问记录；只撤销旧access，不撤销refresh本身。 */
    private void revokePreviousAccessTokens(OAuth2RefreshTokenDO refresh, LocalDateTime now) {
        List<OAuth2AccessTokenDO> previous =
                accessTokenMapper.selectListByRefreshTokenForUpdate(refresh.getRefreshToken());
        if (previous == null || previous.stream().anyMatch(access -> access == null
                || !Objects.equals(access.getRefreshToken(), refresh.getRefreshToken())
                || !Objects.equals(access.getTenantId(), refresh.getTenantId())
                || !Objects.equals(access.getUserId(), refresh.getUserId())
                || !Objects.equals(access.getUserType(), refresh.getUserType())
                || !Objects.equals(access.getClientId(), refresh.getClientId())
                || !StringUtils.hasText(access.getAccessToken()))) {
            throw new IllegalStateException("访问令牌关联异常");
        }
        long expected = previous.stream().filter(access -> access.getRevokedTime() == null).count();
        if (expected > 0) {
            int changed = accessTokenMapper.update(null, new LambdaUpdateWrapper<OAuth2AccessTokenDO>()
                    .eq(OAuth2AccessTokenDO::getRefreshToken, refresh.getRefreshToken())
                    .eq(OAuth2AccessTokenDO::getTenantId, refresh.getTenantId())
                    .isNull(OAuth2AccessTokenDO::getRevokedTime)
                    .set(OAuth2AccessTokenDO::getRevokedTime, now)
                    .set(OAuth2AccessTokenDO::getUpdater, "token-refresh")
                    .set(OAuth2AccessTokenDO::getUpdateTime, now));
            if (changed != expected) {
                throw new IllegalStateException("旧访问令牌撤销结果异常");
            }
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean failed = false;
                for (OAuth2AccessTokenDO access : previous) {
                    try {
                        accessTokenRedisDAO.delete(access.getAccessToken());
                    } catch (RuntimeException ex) {
                        failed = true;
                    }
                }
                if (failed) {
                    throw new IllegalStateException("令牌已刷新，旧缓存清理未完成，请重试刷新");
                }
            }
        });
    }

    /** 当前尚无审计自动填充，归属与审计值均从可信账号和本次时间显式设置。 */
    private void fillAudit(TenantBaseDo record, AdminUserDO user, LocalDateTime now) {
        record.setTenantId(user.getTenantId());
        record.setCreator("admin:" + user.getId());
        record.setUpdater("admin:" + user.getId());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(false);
    }
}
