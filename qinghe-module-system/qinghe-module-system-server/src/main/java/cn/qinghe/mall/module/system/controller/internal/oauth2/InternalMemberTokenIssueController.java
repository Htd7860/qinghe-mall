package cn.qinghe.mall.module.system.controller.internal.oauth2;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.system.api.oauth2.MemberTokenIssueApi;
import cn.qinghe.mall.module.system.api.oauth2.dto.*;
import cn.qinghe.mall.module.system.service.oauth2.MemberTokenIssueService;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;

/** 择取原OAuth2TokenApiImpl签发/刷新/撤销的HTTP委托；无PermitAll，仅供专用服务调用。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
public class InternalMemberTokenIssueController {
    private final MemberTokenIssueService issuer;

    @PostMapping(MemberTokenIssueApi.PATH)
    @PreAuthorize("hasAuthority('internal:member:token-issue')")
    public CommonResult<MemberTokenIssueRespDTO> issue(@Valid @RequestBody MemberTokenIssueReqDTO body,
            HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        final OAuth2TokenPairDTO pair;
        try { pair=issuer.createToken(body.getTenantId(),body.getUserId(),body.getCredentialVersion()); }
        catch (ServiceException invalid) {
            if (Integer.valueOf(401).equals(invalid.getCode())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return CommonResult.error(401,"会员凭据已失效");
            }
            throw invalid;
        }
        MemberTokenIssueRespDTO result=new MemberTokenIssueRespDTO();
        result.setTenantId(body.getTenantId());result.setUserId(body.getUserId());result.setCredentialVersion(body.getCredentialVersion());
        result.setAccessToken(pair.getAccessToken());result.setRefreshToken(pair.getRefreshToken());
        result.setExpiresTime(pair.getExpiresTime());result.setRefreshExpiresTime(pair.getRefreshExpiresTime());
        return CommonResult.success(result);
    }

    @PostMapping(MemberTokenIssueApi.REFRESH_PATH)
    @PreAuthorize("hasAuthority('internal:member:token-refresh')")
    public CommonResult<MemberTokenIssueRespDTO> refresh(@Valid @RequestBody MemberTokenRefreshReqDTO body,
            HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try { return CommonResult.success(issuer.refreshAccessToken(body.getTenantId(),body.getRefreshToken())); }
        catch (ServiceException invalid) {
            if (Integer.valueOf(401).equals(invalid.getCode())) {
                response.setStatus(HttpServletResponse.SC_OK);return CommonResult.error(401,"刷新凭据已失效");
            }
            throw invalid;
        }
    }

    @PostMapping(MemberTokenIssueApi.LOGOUT_PATH)
    @PreAuthorize("hasAuthority('internal:member:token-logout')")
    public CommonResult<Boolean> logout(@Valid @RequestBody MemberTokenLogoutReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try { issuer.logout(body.getAccessToken());return CommonResult.success(true); }
        catch (ServiceException invalid) {
            if (Integer.valueOf(401).equals(invalid.getCode())) {
                response.setStatus(HttpServletResponse.SC_OK);return CommonResult.error(401,"消费者退出凭据无效");
            }
            throw invalid;
        }
    }
}
