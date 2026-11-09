package cn.qinghe.mall.module.system.controller.admin.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.qinghe.mall.module.system.controller.admin.auth.vo.AuthTokenRespVO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.service.auth.AdminAuthService;
import cn.qinghe.mall.module.system.service.auth.LoginFailureLimiter;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Collections;
import java.util.function.Supplier;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.TOO_MANY_REQUESTS;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;

/** 商家和平台入口分别绑定固定客户端；密码校验和签发仍由既有Service负责。 */
@RestController
public class AdminAuthController {

    private final AdminAuthService authService;
    private final TenantService tenantService;
    private final LoginFailureLimiter loginFailureLimiter;

    public AdminAuthController(AdminAuthService authService, TenantService tenantService,
                               LoginFailureLimiter loginFailureLimiter) {
        this.authService = authService;
        this.tenantService = tenantService;
        this.loginFailureLimiter = loginFailureLimiter;
    }

    @PostMapping(value = "/admin-api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<AuthTokenRespVO> loginMerchant(
            @Valid @RequestBody AuthLoginReqVO reqVO, HttpServletResponse response) {
        if (!StringUtils.hasText(reqVO.getTenant())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        return protectedLogin(reqVO, "qinghe-admin", response, () -> {
            TenantDO tenant = tenantService.getTenantByCode(reqVO.getTenant());
            if (tenant == null || Boolean.TRUE.equals(tenant.getDeleted())) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            if (tenant.getId() == null || tenant.getId() <= 0
                    || !reqVO.getTenant().equals(tenant.getCode())) {
                throw new IllegalStateException("登录商家身份数据异常");
            }
            return login(reqVO, tenant.getId(), "qinghe-admin", response);
        });
    }

    @PostMapping(value = "/platform-api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    public CommonResult<AuthTokenRespVO> loginPlatform(
            @Valid @RequestBody AuthLoginReqVO reqVO, HttpServletResponse response) {
        if (StringUtils.hasText(reqVO.getTenant())) {
            throw exception(BAD_REQUEST);
        }
        return protectedLogin(reqVO, "qinghe-platform", response,
                () -> login(reqVO, 0L, "qinghe-platform", response));
    }

    private CommonResult<AuthTokenRespVO> protectedLogin(
            AuthLoginReqVO reqVO, String clientId, HttpServletResponse response,
            Supplier<CommonResult<AuthTokenRespVO>> action) {
        try {
            return loginFailureLimiter.execute(clientId,
                    "qinghe-platform".equals(clientId) ? "" : reqVO.getTenant(),
                    reqVO.getUsername(), action);
        } catch (LoginFailureLimiter.LimitExceededException ex) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()));
            throw exception(TOO_MANY_REQUESTS);
        }
    }

    private CommonResult<AuthTokenRespVO> login(
            AuthLoginReqVO reqVO, Long tenantId, String clientId, HttpServletResponse response) {
        // tenant-id只是客户端声明，不能覆盖入口和商家编码确定的归属。
        Long requestTenantId = TenantContextHolder.getTenantId();
        if (requestTenantId != null && !tenantId.equals(requestTenantId)) {
            throw exception(FORBIDDEN);
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        OAuth2TokenPairDTO pair = authService.login(
                reqVO.getUsername(), reqVO.getPassword(), tenantId,
                clientId, Collections.emptyList());
        return CommonResult.success(AuthTokenRespVO.from(pair));
    }
}
