package cn.qinghe.mall.module.promotion.framework.member;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="qinghe-coupon-storefront",contextId="qingheCouponStorefront",
    url="${qinghe.security.remote.system-base-url:http://127.0.0.1:8080}",configuration=SystemSecurityFeignConfiguration.class)
public interface CouponStorefrontApi {
    /** 仅使用promotion专用只读服务凭据；复用system原入口解析，不接受浏览器传入Host参数。 */
    @PostMapping(cn.qinghe.mall.module.system.api.tenant.MemberStorefrontApi.PATH)
    CommonResult<MemberStorefrontRespDTO> resolve(@RequestHeader("Authorization") String serviceBearer,@RequestBody MemberStorefrontReqDTO body);
    @PostMapping("/internal-api/auth/storefront/current")
    CommonResult<MemberStorefrontRespDTO> current(@RequestHeader("Authorization") String bearer,@RequestBody MemberStorefrontReqDTO body);
}
