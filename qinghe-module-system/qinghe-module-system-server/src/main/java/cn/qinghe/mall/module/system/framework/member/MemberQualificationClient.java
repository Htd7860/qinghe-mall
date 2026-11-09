package cn.qinghe.mall.module.system.framework.member;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.member.api.auth.MemberQualificationApi;
import cn.qinghe.mall.module.member.api.auth.dto.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 固定member权威来源，每次调用专用服务Bearer，无用户Token递归、缓存或故障放行。 */
@Service @Slf4j
public class MemberQualificationClient {
    private final MemberQualificationApi api;
    private final String serviceToken;
    private final Semaphore calls;
    public MemberQualificationClient(MemberQualificationApi api,MemberQualificationProperties properties) {
        URI uri=URI.create(properties.getMemberBaseUrl());
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("会员资格服务地址配置不正确");
        }
        if ("http".equals(uri.getScheme()) && !("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()))) {
            throw new IllegalArgumentException("非回环会员服务地址必须使用HTTPS");
        }
        String token=properties.getSystemToken();
        if (token==null || !token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}")) { throw new IllegalArgumentException("会员服务凭据配置格式不正确"); }
        this.api=api;this.serviceToken=token;this.calls=new Semaphore(properties.getMaxConcurrentCalls());
    }
    public MemberQualificationRespDTO check(Long userId,Long tenantId,Long credentialVersion) {
        if (userId==null || userId<=0 || tenantId==null || tenantId<=0 || credentialVersion!=null && credentialVersion<0) {
            throw exception0(401,"会员凭据身份不正确");
        }
        if (serviceToken.isEmpty()) { throw exception0(503,"会员资格服务凭据未配置"); }
        if (!calls.tryAcquire()) {
            log.debug("RPC diagnostic operation=member.qualification outcome=capacity_rejected");
            throw exception0(503,"会员资格服务繁忙，请稍后重试");
        }
        long started=System.nanoTime();
        try {
            MemberQualificationReqDTO body=new MemberQualificationReqDTO();body.setUserId(userId);body.setTenantId(tenantId);body.setCredentialVersion(credentialVersion);
            CommonResult<MemberQualificationRespDTO> result=api.check("Bearer "+serviceToken,body);
            log.debug("RPC diagnostic operation=member.qualification outcome=response code={}", result==null?null:result.getCode());
            if (result==null || result.getCode()==null) { throw exception0(503,"会员资格结果不完整"); }
            if (Integer.valueOf(401).equals(result.getCode())) { throw exception0(401,"会员凭据已失效"); }
            if (!result.isSuccess()) { throw exception0(503,"会员资格服务暂不可用"); }
            MemberQualificationRespDTO user=result.getData();
            if (user==null || !Objects.equals(userId,user.getUserId()) || !Objects.equals(tenantId,user.getTenantId())
                    || user.getCredentialVersion()==null || user.getCredentialVersion()<0 || user.getNickname()==null
                    || credentialVersion!=null && !credentialVersion.equals(user.getCredentialVersion())) { throw exception0(503,"会员资格结果与凭据不匹配"); }
            return user;
        } catch (RuntimeException ex) {
            log.debug("RPC diagnostic operation=member.qualification outcome=failed cause={}",ex.getClass().getName());
            throw ex;
        } finally {
            calls.release();
            log.debug("RPC diagnostic operation=member.qualification elapsedMicros={}",(System.nanoTime()-started)/1000);
        }
    }
}
