package cn.qinghe.mall.module.product.framework.storefront;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.system.api.tenant.MemberStorefrontApi;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
/** 复用已接通的system实际入口解析；服务凭据只读，用户仍携原会员Bearer核对。 */
@FeignClient(name="qinghe-product-storefront",contextId="qingheProductStorefront",
        url="${qinghe.security.remote.system-base-url:http://127.0.0.1:8080}",configuration=SystemSecurityFeignConfiguration.class)
public interface ProductStorefrontApi {
    @PostMapping(MemberStorefrontApi.PATH)
    CommonResult<MemberStorefrontRespDTO> resolve(@RequestHeader("Authorization") String serviceBearer,@RequestBody MemberStorefrontReqDTO body);
    @PostMapping("/internal-api/auth/storefront/current")
    CommonResult<MemberStorefrontRespDTO> current(@RequestHeader("Authorization") String memberBearer,@RequestBody MemberStorefrontReqDTO body);
}
