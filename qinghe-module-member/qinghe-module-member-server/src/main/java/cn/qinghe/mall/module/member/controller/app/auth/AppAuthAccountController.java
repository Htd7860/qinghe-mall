package cn.qinghe.mall.module.member.controller.app.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthAccountReqVO;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthSendCodeReqVO;
import cn.qinghe.mall.module.member.service.auth.MemberStorefrontAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/** 择取原AppAuthController.sendSmsCode、AppMemberUserController.resetUserPassword薄委托。
 * 注册按已确认的显式密码+REGISTER验证码流程，不移植短信自动开户。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
public class AppAuthAccountController {
    private final MemberStorefrontAccountService accounts;

    @PostMapping(value="/app-api/auth/register",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<Boolean> register(@Valid @RequestBody AppAuthAccountReqVO body,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return CommonResult.success(accounts.register(request,body.getTenant(),body.getMobile(),body.getPassword(),body.getCode()));
    }
    @PostMapping(value="/app-api/auth/reset-password",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<Boolean> resetPassword(@Valid @RequestBody AppAuthAccountReqVO body,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return CommonResult.success(accounts.resetPassword(request,body.getTenant(),body.getMobile(),body.getPassword(),body.getCode()));
    }
    @PostMapping(value="/app-api/auth/send-code",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<Boolean> sendCode(@Valid @RequestBody AppAuthSendCodeReqVO body,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return CommonResult.success(accounts.sendCode(request,body.getTenant(),body.getMobile(),body.getPurpose()));
    }
}
