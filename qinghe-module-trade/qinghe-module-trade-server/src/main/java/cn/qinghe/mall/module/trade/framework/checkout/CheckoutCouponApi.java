package cn.qinghe.mall.module.trade.framework.checkout;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.promotion.api.coupon.dto.CouponRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="qinghe-checkout-coupon",contextId="qingheCheckoutCoupon",url="${qinghe.trade.promotion-base-url:http://127.0.0.1:8085}",configuration=SystemSecurityFeignConfiguration.class)
public interface CheckoutCouponApi {
    @GetMapping("/internal-api/promotion/seckill/checkout-context") CommonResult<cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO> seckill(
            @RequestHeader("Authorization") String bearer,@RequestParam("activityId") Long activity,
            @RequestParam("generation") Integer generation,@RequestParam("skuId") Long sku,@RequestParam("quantity") Integer quantity);
    @GetMapping("/internal-api/promotion/coupon/current") CommonResult<CouponRespDTO> coupon(@RequestHeader("Authorization") String bearer,@RequestParam("id") Long id);
}
