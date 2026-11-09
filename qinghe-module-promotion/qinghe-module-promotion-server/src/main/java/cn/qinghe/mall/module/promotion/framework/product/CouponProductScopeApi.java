package cn.qinghe.mall.module.promotion.framework.product;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.product.api.coupon.dto.CouponProductScopeReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
/** 原商品API校验调用职责，固定product来源且沿用无重试/无凭据日志配置。 */
@FeignClient(name="qinghe-coupon-product-scope",contextId="qingheCouponProductScope",
        url="${qinghe.promotion.product-base-url:http://127.0.0.1:8082}",configuration=SystemSecurityFeignConfiguration.class)
public interface CouponProductScopeApi {
    @PostMapping("/internal-api/product/trade-skus/list")
    CommonResult<java.util.List<cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO>> sales(
            @RequestHeader("Authorization") String bearer,@RequestBody java.util.List<Long> ids);
    @PostMapping("/internal-api/product/coupon-scope/validate")
    CommonResult<Boolean> validate(@RequestHeader("Authorization") String bearer,@RequestBody CouponProductScopeReqDTO body);
}
