package cn.qinghe.mall.module.system.controller.admin.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.auth.vo.AuthRefreshReqVO;
import cn.qinghe.mall.module.system.controller.admin.auth.vo.AuthTokenRespVO;
import cn.qinghe.mall.module.system.service.auth.AdminAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;

/** 刷新不依赖有效access；退出凭据由撤销事务直接核对，不恢复登录身份。 */
@RestController
public class AdminAuthTokenController {

    private final AdminAuthService authService;
    private final SecurityProperties securityProperties;

    public AdminAuthTokenController(AdminAuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @PostMapping(value = {"/admin-api/auth/refresh-token", "/platform-api/auth/refresh-token"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<AuthTokenRespVO> refreshToken(
            @Valid @RequestBody AuthRefreshReqVO reqVO,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return CommonResult.success(AuthTokenRespVO.from(
                authService.refreshToken(reqVO.getRefreshToken(), clientId(request))));
    }

    @PostMapping({"/admin-api/auth/logout", "/platform-api/auth/logout"})
    @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String token;
        try {
            token = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader());
        } catch (BadCredentialsException ex) {
            throw exception(UNAUTHORIZED);
        }
        if (token == null) {
            throw exception(UNAUTHORIZED);
        }
        // 不先调用checkAccessToken：过期、已撤销或账号停用仍须能退出并重试缓存清理。
        authService.logout(token, clientId(request));
        return CommonResult.success(true);
    }

    /** 只由上面四个精确映射调用，不能由请求正文或查询参数选择clientId。 */
    private String clientId(HttpServletRequest request) {
        return request.getServletPath().startsWith("/platform-api/")
                ? "qinghe-platform" : "qinghe-admin";
    }
}
