package cn.qinghe.mall.module.member.controller.app.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.redis.core.ratelimit.LoginFailureLimiter;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthLoginReqVO;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthTokenRespVO;
import cn.qinghe.mall.module.member.service.auth.MemberStorefrontAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原AppAuthController.login的薄委托；不在Controller重新实现密码/Token/租户算法。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
public class AppAuthController {
    private final MemberStorefrontAuthService authentication;

    @PostMapping(value="/app-api/auth/login",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<AppAuthTokenRespVO> login(@Valid @RequestBody AppAuthLoginReqVO body,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        try {
            return CommonResult.success(AppAuthTokenRespVO.from(authentication.login(
                    request,body.getTenant(),body.getMobile(),body.getPassword())));
        } catch (LoginFailureLimiter.LimitExceededException limited) {
            response.setHeader(HttpHeaders.RETRY_AFTER,Long.toString(limited.getRetryAfterSeconds()));
            throw exception0(429,"登录尝试过于频繁，请稍后重试");
        }
    }
}
