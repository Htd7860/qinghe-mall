package cn.qinghe.mall.module.member.framework.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.system.api.oauth2.MemberTokenIssueApi;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueReqDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenRefreshReqDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenLogoutReqDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原MemberAuthServiceImpl.createTokenAfterLoginSuccess的OAuth2 API调用职责。
 * 固定system来源/服务Bearer，调用端不转发密码/用户Bearer或选择角色与客户端。
 * @author 芋道源码
 */
@Service
public class MemberTokenClient {
    private final MemberTokenIssueApi api;
    private final String serviceToken;
    private final Semaphore calls;
    public MemberTokenClient(MemberTokenIssueApi api,MemberTokenClientProperties settings) {
        String token=settings.validatedServiceToken();
        this.api=api;this.serviceToken=token;this.calls=new Semaphore(settings.getMaxConcurrentCalls());
    }
    public MemberTokenIssueRespDTO issue(Long tenantId,Long userId,Long credentialVersion) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("会员签发RPC不能在会员数据库事务内执行");
        }
        if (tenantId==null || tenantId<=0 || userId==null || userId<=0 || credentialVersion==null || credentialVersion<0) {
            throw exception0(401,"会员签发候选身份无效");
        }
        if (serviceToken.isEmpty()) { throw exception0(503,"会员签发服务凭据未配置"); }
        if (!calls.tryAcquire()) { throw exception0(503,"会员签发服务繁忙，请稍后重试"); }
        try {
            MemberTokenIssueReqDTO body=new MemberTokenIssueReqDTO();
            body.setTenantId(tenantId);body.setUserId(userId);body.setCredentialVersion(credentialVersion);
            CommonResult<MemberTokenIssueRespDTO> response;
            try { response=api.issue("Bearer "+serviceToken,body); }
            catch (RuntimeException unknown) {
                // Feign异常可能含Request/原始响应/服务密钥，不记录、不向外携带cause。
                throw exception0(503,"会员签发结果未确认，请重新登录");
            }
            if (response==null || response.getCode()==null) { throw exception0(503,"会员签发响应不完整"); }
            if (Integer.valueOf(401).equals(response.getCode())) { throw exception0(401,"会员凭据已变化，请重新登录"); }
            if (!response.isSuccess()) { throw exception0(503,"会员签发服务暂不可用"); }
            MemberTokenIssueRespDTO pair=response.getData();
            if (pair==null || !Objects.equals(tenantId,pair.getTenantId()) || !Objects.equals(userId,pair.getUserId())
                    || !Objects.equals(credentialVersion,pair.getCredentialVersion())
                    || !token(pair.getAccessToken(),"at_") || !token(pair.getRefreshToken(),"rt_")
                    || pair.getExpiresTime()==null || pair.getRefreshExpiresTime()==null
                    || !pair.getExpiresTime().isAfter(LocalDateTime.now())
                    || pair.getRefreshExpiresTime().isBefore(pair.getExpiresTime())) {
                throw exception0(503,"会员签发结果与候选身份不匹配");
            }
            return pair;
        } finally { calls.release(); }
    }
    /** 原MemberAuthServiceImpl.refreshToken的固定OAuth2客户端委托；不滑动延长refresh期限。 */
    public MemberTokenIssueRespDTO refresh(Long tenantId,String value) {
        requireOutsideTransaction();
        if (tenantId==null || tenantId<=0 || !token(value,"rt_")) { throw exception0(401,"会员刷新凭据无效"); }
        acquire();
        try {
            MemberTokenRefreshReqDTO body=new MemberTokenRefreshReqDTO();
            body.setTenantId(tenantId);body.setRefreshToken(value);
            CommonResult<MemberTokenIssueRespDTO> response;
            try { response=api.refresh("Bearer "+serviceToken,body); }
            catch (RuntimeException unknown) { throw exception0(503,"会员刷新结果未确认，请稍后重试"); }
            requireSuccess(response);
            MemberTokenIssueRespDTO pair=response.getData();
            if (pair==null || !Objects.equals(tenantId,pair.getTenantId())
                    || pair.getUserId()==null || pair.getUserId()<=0
                    || pair.getCredentialVersion()==null || pair.getCredentialVersion()<0
                    || !token(pair.getAccessToken(),"at_") || !value.equals(pair.getRefreshToken())
                    || pair.getExpiresTime()==null || pair.getRefreshExpiresTime()==null
                    || !pair.getExpiresTime().isAfter(LocalDateTime.now())
                    || pair.getRefreshExpiresTime().isBefore(pair.getExpiresTime())) {
                throw exception0(503,"会员刷新结果与原会话不匹配");
            }
            return pair;
        } finally { calls.release(); }
    }

    /** 不先检查access有效期/账号状态；由system固定MEMBER/client核对持久会话并清理。 */
    public void logout(String value) {
        requireOutsideTransaction();
        if (!token(value,"at_")) { throw exception0(401,"会员退出凭据无效"); }
        acquire();
        try {
            MemberTokenLogoutReqDTO body=new MemberTokenLogoutReqDTO();body.setAccessToken(value);
            CommonResult<Boolean> response;
            try { response=api.logout("Bearer "+serviceToken,body); }
            catch (RuntimeException unknown) { throw exception0(503,"会员退出结果未确认，请重试退出"); }
            requireSuccess(response);
            if (!Boolean.TRUE.equals(response.getData())) { throw exception0(503,"会员退出结果未确认，请重试退出"); }
        } finally { calls.release(); }
    }
    private void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("会员令牌RPC不能在会员数据库事务内执行");
        }
    }
    private void acquire() {
        if (serviceToken.isEmpty()) { throw exception0(503,"会员令牌服务凭据未配置"); }
        if (!calls.tryAcquire()) { throw exception0(503,"会员令牌服务繁忙，请稍后重试"); }
    }
    private void requireSuccess(CommonResult<?> response) {
        if (response==null || response.getCode()==null) { throw exception0(503,"会员令牌响应不完整"); }
        if (Integer.valueOf(401).equals(response.getCode())) { throw exception0(401,"会员凭据无效，请重新登录"); }
        if (!response.isSuccess()) { throw exception0(503,"会员令牌服务暂不可用"); }
    }
    private boolean token(String value,String prefix) {
        if (value==null || !value.matches(prefix+"[A-Za-z0-9_-]{43}")) { return false; }
        String encoded=value.substring(prefix.length());
        try {
            byte[] decoded=Base64.getUrlDecoder().decode(encoded);
            return decoded.length==32 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(encoded);
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
