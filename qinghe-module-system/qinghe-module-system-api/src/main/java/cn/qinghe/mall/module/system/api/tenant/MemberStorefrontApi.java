package cn.qinghe.mall.module.system.api.tenant;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.system.api.oauth2.MemberTokenFeignConfiguration;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 原getTenantByWebsite职责的内部适配；服务认证后复用当前唯一入口映射。
 * @author 芋道源码
 */
@FeignClient(name="qinghe-system-member-entry",contextId="qingheMemberStorefront",
        url="${qinghe.member-token.system-base-url:http://127.0.0.1:8080}",
        configuration=MemberTokenFeignConfiguration.class)
public interface MemberStorefrontApi {
    String PATH="/internal-api/system/member-storefront/resolve";
    @PostMapping(PATH)
    CommonResult<MemberStorefrontRespDTO> resolve(@RequestHeader("Authorization") String serviceAuthorization,
            @RequestBody MemberStorefrontReqDTO body);
}
