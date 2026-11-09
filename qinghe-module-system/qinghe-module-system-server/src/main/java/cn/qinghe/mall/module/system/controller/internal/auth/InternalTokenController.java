package cn.qinghe.mall.module.system.controller.internal.auth;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;

/** 受现有Bearer安全链保护，只返回当前凭据已认证的身份，不接收任意待检查Token。 */
@RestController
public class InternalTokenController {

    @GetMapping("/internal-api/auth/token/check")
    public CommonResult<OAuth2AccessTokenCheckRespDTO> checkCurrentToken(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            throw exception(UNAUTHORIZED);
        }
        OAuth2AccessTokenCheckRespDTO result = new OAuth2AccessTokenCheckRespDTO();
        result.setUserId(user.getId());
        result.setUserType(user.getUserType());
        result.setTenantId(user.getTenantId());
        result.setUserInfo(user.getInfo() == null ? new HashMap<>() : new HashMap<>(user.getInfo()));
        result.setScopes(user.getScopes() == null ? new ArrayList<>() : new ArrayList<>(user.getScopes()));
        result.setExpiresTime(user.getExpiresTime());
        return CommonResult.success(result);
    }
}
