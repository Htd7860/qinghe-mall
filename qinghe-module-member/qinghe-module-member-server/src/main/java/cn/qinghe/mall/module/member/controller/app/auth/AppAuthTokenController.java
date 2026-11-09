package cn.qinghe.mall.module.member.controller.app.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthRefreshReqVO;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthTokenRespVO;
import cn.qinghe.mall.module.member.service.auth.MemberStorefrontAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原AppAuthController.refreshToken/logout：持有凭据操作，不要求有效access身份。
 * @author 芋道源码
 */
@RestController
@Validated
@RequiredArgsConstructor
public class AppAuthTokenController {
    private final MemberStorefrontAuthService authentication;
    private final SecurityProperties security;

    @PostMapping(value="/app-api/auth/refresh-token",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<AppAuthTokenRespVO> refreshToken(@Valid @RequestBody AppAuthRefreshReqVO body,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return CommonResult.success(AppAuthTokenRespVO.from(
                authentication.refresh(request,store,body.getRefreshToken())));
    }

    @PostMapping("/app-api/auth/logout")
    @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        String value;
        try { value=SecurityFrameworkUtils.obtainAuthorization(request,security.getTokenHeader()); }
        catch (BadCredentialsException invalid) { throw exception0(401,"会员退出凭据格式不正确"); }
        if (value==null) { throw exception0(401,"请提供会员退出凭据"); }
        authentication.logout(value);
        return CommonResult.success(true);
    }
}
