package cn.qinghe.mall.framework.security.core.rpc;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.PermissionCheckReqDTO;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 沿用yudao的Feign内部API形式；身份契约适配DEC-060，每次传递原Bearer。
 * Token不进入URL、日志或可伪造的login-user头。
 */
@FeignClient(name = "qinghe-system-security", contextId = "qingheSystemSecurity",
        url = "${qinghe.security.remote.system-base-url:http://127.0.0.1:8080}",
        configuration = SystemSecurityFeignConfiguration.class)
public interface SystemSecurityFeignApi {
    @GetMapping("/internal-api/auth/product-owner")
    CommonResult<cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO> productOwner(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @org.springframework.web.bind.annotation.RequestParam("id") Long id);

    @GetMapping("/internal-api/auth/data-scope/current")
    CommonResult<UserDataScopeRespDTO> currentDataScope(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/internal-api/auth/tenant/operation/check")
    CommonResult<Boolean> checkTenantOperation(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/internal-api/auth/token/check")
    CommonResult<OAuth2AccessTokenCheckRespDTO> checkToken(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostMapping("/internal-api/auth/permission/check")
    CommonResult<Boolean> checkPermission(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestBody PermissionCheckReqDTO body);
}
