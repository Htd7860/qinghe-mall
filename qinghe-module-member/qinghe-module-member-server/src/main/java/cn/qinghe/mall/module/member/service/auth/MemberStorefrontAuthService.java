package cn.qinghe.mall.module.member.service.auth;

import cn.qinghe.mall.module.member.framework.auth.MemberStorefrontClient;
import cn.qinghe.mall.module.member.framework.auth.MemberLoginFailureLimiter;
import cn.qinghe.mall.module.member.framework.auth.MemberTokenClient;
import cn.qinghe.mall.framework.redis.core.ratelimit.LoginFailureLimiter;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原会员认证调用链的品牌入口适配；登录记录在member库落地。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MemberStorefrontAuthService {
    private final MemberStorefrontClient storefronts;
    private final MemberAuthService authentication;
    private final MemberLoginFailureLimiter failures;
    private final MemberLoginLogService history;
    private final MemberTokenClient tokens;
    private final AtomicBoolean historyUnavailable=new AtomicBoolean();

    public MemberTokenIssueRespDTO login(HttpServletRequest request,String store,String mobile,String password) {
        if (mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")) { throw exception0(400,"手机号格式不正确"); }
        MemberStorefrontRespDTO entry=storefronts.resolveRequest(request,store);
        // 只记录member实际观察到的传输对端；不把代理IP或任意X-Forwarded-For冒称真实浏览器IP。
        String peer=request.getRemoteAddr();
        if (peer==null || !peer.matches("[0-9A-Fa-f:.]{2,45}")) { throw exception0(503,"暂时无法确认登录来源"); }
        String traceId=UUID.randomUUID().toString().replace("-","");
        try {
            return failures.execute(entry.getTenantId(),mobile,() -> {
                MemberTokenIssueRespDTO pair=authentication.login(entry.getTenantId(),mobile,password);
                // system可能已经提交；入口变化时不返回这对Token，不声称跨服务回滚或自动重新签发。
                storefronts.requireSameRequest(request,store,entry);
                if (!pair.getExpiresTime().isAfter(LocalDateTime.now())) {
                    throw exception0(503,"会员登录处理超时，请重新登录");
                }
                try {
                    history.recordSuccess(entry.getTenantId(),pair.getUserId(),pair.getCredentialVersion(),mobile,peer,traceId);
                    recovered();
                } catch (ServiceException changed) { throw changed; }
                catch (RuntimeException unavailable) {
                    throw exception0(503,"令牌签发可能已完成，登录记录未确认，请重新登录");
                }
                if (!pair.getExpiresTime().isAfter(LocalDateTime.now())) {
                    throw exception0(503,"令牌已签发但处理期间过期，请重新登录");
                }
                return pair;
            });
        } catch (LoginFailureLimiter.LimitExceededException limited) {
            recordFailure(entry.getTenantId(),mobile,peer,traceId,MemberLoginLogService.Result.LIMITED);throw limited;
        } catch (ServiceException rejected) {
            recordFailure(entry.getTenantId(),mobile,peer,traceId,Integer.valueOf(401).equals(rejected.getCode())
                    ?MemberLoginLogService.Result.BAD_CREDENTIALS:MemberLoginLogService.Result.ERROR);
            throw rejected;
        } catch (RuntimeException unavailable) {
            recordFailure(entry.getTenantId(),mobile,peer,traceId,MemberLoginLogService.Result.ERROR);
            // 不让可能含手机号/SQL或上游凭据的异常进入对外错误和技术日志。
            throw exception0(503,"会员登录处理未确认，请稍后重试");
        }
    }
    /** 只传实际入口解析的tenant；请求不能挑选user/client/credentialVersion。 */
    public MemberTokenIssueRespDTO refresh(HttpServletRequest request,String store,String refreshToken) {
        MemberStorefrontRespDTO entry=storefronts.resolveRequest(request,store);
        MemberTokenIssueRespDTO pair=tokens.refresh(entry.getTenantId(),refreshToken);
        // system可能已提交，入口变化时不交付Token，也不自动换店/再次签发。
        storefronts.requireSameRequest(request,store,entry);
        if (!pair.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw exception0(503,"会员刷新处理超时，请稍后重试");
        }
        return pair;
    }
    public void logout(String accessToken) { tokens.logout(accessToken); }

    private void recordFailure(Long tenantId,String mobile,String peer,String traceId,MemberLoginLogService.Result result) {
        try { history.recordFailure(tenantId,mobile,peer,traceId,result);recovered(); }
        catch (RuntimeException unavailable) {
            // 失败审计不可用不覆盖原401/限额，也不自动重做密码校验或Token签发。
            if (historyUnavailable.compareAndSet(false,true)) { log.warn("Member login history unavailable"); }
        }
    }
    private void recovered() {
        if (historyUnavailable.compareAndSet(true,false)) { log.info("Member login history recovered"); }
    }
}
