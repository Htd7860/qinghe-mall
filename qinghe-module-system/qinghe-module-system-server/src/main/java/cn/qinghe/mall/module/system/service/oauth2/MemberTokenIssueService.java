package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.member.api.auth.dto.MemberQualificationRespDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.*;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.*;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantPackageMapper;
import cn.qinghe.mall.module.system.framework.member.MemberQualificationClient;
import cn.qinghe.mall.module.system.framework.member.MemberTokenIssueProperties;
import cn.qinghe.mall.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取yudao OAuth2TokenServiceImpl的refresh/access生成、保存与归属流程。
 * 沿用现有后台两表本地事务/生成器；真实MEMBER资格来自独立会员服务，不访问员工表。
 * @author 芋道源码
 */
@Service
@lombok.extern.slf4j.Slf4j
public class MemberTokenIssueService {
    public static final String CLIENT_ID="qinghe-app";
    private final OAuth2ClientMapper clients;
    private final TenantMapper tenants;
    private final TenantPackageMapper packages;
    private final OAuth2RefreshTokenMapper refreshTokens;
    private final OAuth2AccessTokenMapper accessTokens;
    private final OAuth2ClientService clientService;
    private final TenantService tenantService;
    private final MemberQualificationClient members;
    private final OAuth2AccessTokenRedisDAO cache;
    private final OAuth2TokenRevokeService revoker;
    private final Semaphore calls;

    public MemberTokenIssueService(OAuth2ClientMapper clients,TenantMapper tenants,TenantPackageMapper packages,
            OAuth2RefreshTokenMapper refreshTokens,OAuth2AccessTokenMapper accessTokens,OAuth2ClientService clientService,
            TenantService tenantService,MemberQualificationClient members,MemberTokenIssueProperties settings,
            OAuth2AccessTokenRedisDAO cache,OAuth2TokenRevokeService revoker) {
        this.clients=clients;this.tenants=tenants;this.packages=packages;this.refreshTokens=refreshTokens;
        this.accessTokens=accessTokens;this.clientService=clientService;this.tenantService=tenantService;this.members=members;
        this.cache=cache;this.revoker=revoker;
        this.calls=new Semaphore(settings.getMaxConcurrent());
    }

    /** 原refreshAccessToken流程：保留refresh绝对期限，锁父会话后撤销旧access并生成新access。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,
            rollbackFor=Exception.class,timeout=10)
    public MemberTokenIssueRespDTO refreshAccessToken(Long tenantId,String value) {
        MybatisUtils.requireWriteTransaction();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { throw new IllegalStateException("会员刷新需要事务同步回调"); }
        if (tenantId==null || tenantId<=0 || value==null || !value.matches("rt_[A-Za-z0-9_-]{43}")) {
            throw exception0(401,"刷新凭据无效");
        }
        if (TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId()!=null
                && !tenantId.equals(TenantContextHolder.getTenantId())) { throw exception0(403,"刷新租户与入口不匹配"); }
        if (!calls.tryAcquire()) { throw exception0(503,"会员令牌服务繁忙，请稍后重试"); }
        try {
            OAuth2RefreshTokenDO refresh=refreshTokens.selectByRefreshTokenForUpdate(value);
            if (refresh==null || !value.equals(refresh.getRefreshToken()) || Boolean.TRUE.equals(refresh.getDeleted()) || refresh.getRevokedTime()!=null
                    || !CLIENT_ID.equals(refresh.getClientId()) || !UserTypeEnum.MEMBER.getValue().equals(refresh.getUserType())
                    || !tenantId.equals(refresh.getTenantId()) || refresh.getId()==null || refresh.getId()<=0
                    || refresh.getUserId()==null || refresh.getUserId()<=0 || refresh.getCredentialVersion()==null
                    || refresh.getCredentialVersion()<0 || refresh.getExpiresTime()==null || refresh.getScopes()==null
                    || !refresh.getScopes().isEmpty() || !refresh.getExpiresTime().isAfter(LocalDateTime.now())) {
                throw exception0(401,"刷新凭据已失效");
            }
            return TenantUtils.execute(tenantId,() -> {
                lockQualification(tenantId);tenantService.validateForAuthentication(tenantId);
                OAuth2ClientDO client=clientService.validateClient(CLIENT_ID,UserTypeEnum.MEMBER.getValue(),tenantId,refresh.getScopes());
                MemberQualificationRespDTO user=members.check(refresh.getUserId(),tenantId,refresh.getCredentialVersion());
                LocalDateTime now=LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
                if (!refresh.getExpiresTime().isAfter(now)) { throw exception0(401,"刷新凭据已过期"); }
                revokePreviousAccess(refresh,now);
                LocalDateTime issuedAt=LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
                if (!refresh.getExpiresTime().isAfter(issuedAt)) { throw exception0(401,"刷新凭据已过期"); }
                OAuth2TokenPairDTO pair=persistAccessToken(user,client,refresh,issuedAt);
                members.check(refresh.getUserId(),tenantId,refresh.getCredentialVersion());
                if (!pair.getExpiresTime().isAfter(LocalDateTime.now())) { throw exception0(401,"刷新处理期间凭据已到期"); }
                MemberTokenIssueRespDTO result=new MemberTokenIssueRespDTO();
                result.setTenantId(tenantId);result.setUserId(refresh.getUserId());result.setCredentialVersion(refresh.getCredentialVersion());
                result.setAccessToken(pair.getAccessToken());result.setRefreshToken(pair.getRefreshToken());
                result.setExpiresTime(pair.getExpiresTime());result.setRefreshExpiresTime(pair.getRefreshExpiresTime());
                return result;
            });
        } catch (DataAccessException failure) {
            Throwable root=failure.getMostSpecificCause();
            log.warn("Member refresh persistence failed; type={}, sqlCode={}, location={}",root.getClass().getSimpleName(),
                    root instanceof java.sql.SQLException?((java.sql.SQLException)root).getErrorCode():0,
                    java.util.Arrays.stream(root.getStackTrace()).filter(frame -> frame.getClassName().startsWith("cn.qinghe.")).findFirst().map(Object::toString).orElse("unknown"));
            throw exception0(503,"会员刷新持久化结果未确认，请重新查询登录状态");
        } finally { calls.release(); }
    }

    /** 退出只清理持有凭据的固定消费者会话，不恢复身份；允许过期/已撤销凭据重试。 */
    public void logout(String value) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("会员退出入口不得嵌套外层数据库事务");
        }
        if (value==null || !value.matches("at_[A-Za-z0-9_-]{43}") || TenantContextHolder.isIgnore()) {
            throw exception0(401,"消费者退出凭据无效");
        }
        if (!calls.tryAcquire()) { throw exception0(503,"会员令牌服务繁忙，请稍后重试"); }
        try {
            OAuth2AccessTokenDO access=accessTokens.selectByAccessToken(value);
            if (access!=null && (!CLIENT_ID.equals(access.getClientId()) || !UserTypeEnum.MEMBER.getValue().equals(access.getUserType())
                    || access.getTenantId()==null || access.getTenantId()<=0)) { throw exception0(401,"消费者退出凭据无效"); }
            // 直接复用现有两表撤销/父子锁/提交后删缓存；client不由请求选择。
            revoker.revokeAccessToken(value,CLIENT_ID);
        } catch (DataAccessException failure) {
            throw exception0(503,"会员退出持久化结果未确认，请重试退出");
        } finally { calls.release(); }
    }

    /** 仅供专用已认证member调用者；密码验证发生在会员库，不能传入浏览器自报的候选。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,
            rollbackFor=Exception.class,timeout=10)
    public OAuth2TokenPairDTO createToken(Long tenantId,Long userId,Long credentialVersion) {
        MybatisUtils.requireWriteTransaction();
        if (tenantId==null || tenantId<=0 || userId==null || userId<=0 || credentialVersion==null || credentialVersion<0) {
            throw exception0(401,"会员签发身份不完整");
        }
        if (TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId()!=null
                && !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw exception0(403,"会员签发租户不匹配");
        }
        if (!calls.tryAcquire()) { throw exception0(503,"会员签发服务繁忙，请稍后重试"); }
        try {
            return TenantUtils.execute(tenantId,() -> {
                lockQualification(tenantId);
                tenantService.validateForAuthentication(tenantId);
                OAuth2ClientDO client=clientService.validateClient(CLIENT_ID,UserTypeEnum.MEMBER.getValue(),
                        tenantId,Collections.emptyList());
                MemberQualificationRespDTO user=members.check(userId,tenantId,credentialVersion);
                OAuth2TokenPairDTO pair=persistPair(user,client);
                // 会员在另一个库：提交前再查代次/启用状态，但不将RPC描述为跨库锁或原子提交。
                members.check(userId,tenantId,credentialVersion);
                if (!pair.getExpiresTime().isAfter(LocalDateTime.now())) {
                    throw exception0(503,"会员签发处理超时，请重新登录");
                }
                return pair;
            });
        } catch (DataAccessException failure) {
            // 不携带可能包含原始Token的SQL错误或cause。
            throw exception0(503,"会员令牌持久化结果未确认，请重新登录");
        } finally { calls.release(); }
    }

    /** 固定客户端→租户→关联套餐，与已有后台签发顺序一致，不锁员工记录。 */
    private void lockQualification(Long tenantId) {
        OAuth2ClientDO client=clients.selectOne(new LambdaQueryWrapper<OAuth2ClientDO>()
                .eq(OAuth2ClientDO::getClientId,CLIENT_ID).last("FOR SHARE"));
        if (client==null) { throw exception0(401,"消费者客户端不可用"); }
        TenantDO tenant=tenants.selectOne(new LambdaQueryWrapper<TenantDO>()
                .eq(TenantDO::getId,tenantId).last("FOR SHARE"));
        if (tenant==null) { throw exception0(401,"消费者店铺不可用"); }
        if (tenant.getPackageId()==null || tenant.getPackageId()<=0) { throw exception0(503,"店铺套餐配置异常"); }
        TenantPackageDO pack=packages.selectOne(new LambdaQueryWrapper<TenantPackageDO>()
                .eq(TenantPackageDO::getId,tenant.getPackageId()).last("FOR SHARE"));
        if (pack==null) { throw exception0(503,"店铺套餐不存在"); }
    }

    /** 原createOAuth2RefreshToken/createOAuth2AccessToken两表保存流程；不提前写Redis。 */
    private OAuth2TokenPairDTO persistPair(MemberQualificationRespDTO user,OAuth2ClientDO client) {
        LocalDateTime now=LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime refreshExpires=now.plusSeconds(client.getRefreshTokenValiditySeconds());
        OAuth2RefreshTokenDO refresh=new OAuth2RefreshTokenDO();
        refresh.setRefreshToken(OAuth2TokenGenerator.generateRefreshToken());
        refresh.setUserId(user.getUserId());refresh.setUserType(UserTypeEnum.MEMBER.getValue());
        refresh.setCredentialVersion(user.getCredentialVersion());refresh.setClientId(CLIENT_ID);
        refresh.setScopes(Collections.emptyList());refresh.setExpiresTime(refreshExpires);
        fillAudit(refresh,user,now);
        if (refreshTokens.insert(refresh)!=1 || refresh.getId()==null || refresh.getId()<=0) {
            throw new IllegalStateException("会员刷新令牌保存失败");
        }
        return persistAccessToken(user,client,refresh,now);
    }

    /** 登录和刷新共用access保存，不重建refresh或把当前代次补给旧会话。 */
    private OAuth2TokenPairDTO persistAccessToken(MemberQualificationRespDTO user,OAuth2ClientDO client,
            OAuth2RefreshTokenDO refresh,LocalDateTime now) {
        MybatisUtils.requireWriteTransaction();
        if (!Objects.equals(user.getTenantId(),refresh.getTenantId()) || !Objects.equals(user.getUserId(),refresh.getUserId())
                || !Objects.equals(user.getCredentialVersion(),refresh.getCredentialVersion()) || !CLIENT_ID.equals(client.getClientId())) {
            throw new IllegalStateException("会员访问令牌与会话归属不一致");
        }
        LocalDateTime refreshExpires=refresh.getExpiresTime();
        LocalDateTime accessExpires=now.plusSeconds(client.getAccessTokenValiditySeconds());
        if (accessExpires.isAfter(refreshExpires)) { accessExpires=refreshExpires; }
        Map<String,String> info=new HashMap<>();
        info.put("credentialVersion",user.getCredentialVersion().toString());info.put("nickname",user.getNickname());
        OAuth2AccessTokenDO access=new OAuth2AccessTokenDO();
        access.setAccessToken(OAuth2TokenGenerator.generateAccessToken());access.setRefreshToken(refresh.getRefreshToken());
        access.setUserId(user.getUserId());access.setUserType(UserTypeEnum.MEMBER.getValue());access.setUserInfo(info);
        access.setClientId(CLIENT_ID);access.setScopes(Collections.emptyList());access.setExpiresTime(accessExpires);
        fillAudit(access,user,now);
        if (accessTokens.insert(access)!=1 || access.getId()==null || access.getId()<=0) {
            throw new IllegalStateException("会员访问令牌保存失败");
        }
        OAuth2TokenPairDTO pair=new OAuth2TokenPairDTO();pair.setAccessToken(access.getAccessToken());
        pair.setRefreshToken(refresh.getRefreshToken());pair.setExpiresTime(accessExpires);pair.setRefreshExpiresTime(refreshExpires);
        return pair;
    }

    /** 与后台现有刷新保持同一父→子锁序和批量失效流程；仅在成功提交后清理快照。 */
    private void revokePreviousAccess(OAuth2RefreshTokenDO refresh,LocalDateTime now) {
        List<OAuth2AccessTokenDO> previous=accessTokens.selectListByRefreshTokenForUpdate(refresh.getRefreshToken());
        if (previous==null || previous.stream().anyMatch(access -> access==null || access.getId()==null || access.getId()<=0
                || !Objects.equals(refresh.getRefreshToken(),access.getRefreshToken())
                || !Objects.equals(refresh.getTenantId(),access.getTenantId()) || !Objects.equals(refresh.getUserId(),access.getUserId())
                || !UserTypeEnum.MEMBER.getValue().equals(access.getUserType()) || !CLIENT_ID.equals(access.getClientId())
                || access.getAccessToken()==null || !access.getAccessToken().matches("at_[A-Za-z0-9_-]{43}"))) {
            throw new IllegalStateException("会员访问令牌关联异常");
        }
        long expected=previous.stream().filter(access -> access.getRevokedTime()==null).count();
        if (expected>0) {
            int changed=accessTokens.update(null,new LambdaUpdateWrapper<OAuth2AccessTokenDO>()
                    .eq(OAuth2AccessTokenDO::getRefreshToken,refresh.getRefreshToken())
                    .eq(OAuth2AccessTokenDO::getTenantId,refresh.getTenantId())
                    .isNull(OAuth2AccessTokenDO::getRevokedTime).set(OAuth2AccessTokenDO::getRevokedTime,now)
                    .set(OAuth2AccessTokenDO::getUpdater,"member-refresh").set(OAuth2AccessTokenDO::getUpdateTime,now));
            if (changed!=expected) { throw new IllegalStateException("会员旧访问凭据失效结果异常"); }
        }
        List<String> keys=new ArrayList<>();for (OAuth2AccessTokenDO access:previous) { keys.add(access.getAccessToken()); }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                boolean failed=false;
                for (String key:keys) { try { cache.delete(key); } catch (RuntimeException unavailable) { failed=true; } }
                if (failed) { throw exception0(503,"会员刷新已提交，旧缓存清理未确认，请重试"); }
            }
        });
    }
    private void fillAudit(TenantBaseDo row,MemberQualificationRespDTO user,LocalDateTime now) {
        row.setTenantId(user.getTenantId());row.setCreator("member:"+user.getUserId());row.setUpdater("member:"+user.getUserId());
        row.setCreateTime(now);row.setUpdateTime(now);row.setDeleted(false);
    }
}
