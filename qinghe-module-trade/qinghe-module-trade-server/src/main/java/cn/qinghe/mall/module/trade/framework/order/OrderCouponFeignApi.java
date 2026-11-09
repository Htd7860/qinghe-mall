package cn.qinghe.mall.module.trade.framework.order;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 择取原 CouponApi 委托；创建阶段占用/核销、未付关闭退券和原订单结果查询。
 * @author 芋道源码
 */
@FeignClient(name="qinghe-order-coupon",contextId="qingheOrderCoupon",url="${qinghe.trade.promotion-base-url:http://127.0.0.1:8085}",configuration=OrderResourceFeignConfiguration.class)
public interface OrderCouponFeignApi {
    @PostMapping("/internal-api/promotion/seckill-refund/result")
    CommonResult<cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillRefundResourceReceiptDTO> seckillRefundResult(
            @RequestHeader("Authorization") String service,@RequestHeader("tenant-id") Long tenant,
            @RequestBody cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillRefundResourceLookupDTO body);
    @PostMapping("/internal-api/promotion/seckill-refund/recover")
    CommonResult<cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillRefundResourceReceiptDTO> seckillRefundRecover(
            @RequestHeader("Authorization") String service,@RequestHeader("tenant-id") Long tenant,
            @RequestBody cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillRefundResourceLookupDTO body);
    @PostMapping("/internal-api/promotion/seckill-order/payment-eligibility")
    CommonResult<cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillPaymentEligibilityDTO> seckillPaymentEligibility(
            @RequestHeader("Authorization") String service,@RequestHeader("tenant-id") Long tenant,
            @RequestBody cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillResourceLookupDTO body);
    @PostMapping("/internal-api/promotion/seckill-order/resource-result")
    CommonResult<cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillResourceReceiptDTO> seckillResource(
            @RequestHeader("Authorization") String service,@RequestHeader("tenant-id") Long tenant,
            @RequestBody cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillResourceLookupDTO body);
    @PostMapping("/internal-api/promotion/seckill-event/read")
    CommonResult<cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillDispatchSnapshotDTO> seckillEvent(
            @RequestHeader("Authorization") String service,@RequestHeader("tenant-id") Long tenant,
            @RequestBody cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillEventLookupReqDTO body);
    @PostMapping("/internal-api/promotion/order-coupon/return-refund")
    CommonResult<CouponOrderResultDTO> returnRefund(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody CouponOrderCommandReqDTO body);
    @PostMapping("/internal-api/promotion/order-coupon/reserve")
    CommonResult<CouponOrderResultDTO> reserve(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody CouponOrderReserveReqDTO body);
    @PostMapping("/internal-api/promotion/order-coupon/use")
    CommonResult<CouponOrderResultDTO> use(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody CouponOrderCommandReqDTO body);
    @PostMapping("/internal-api/promotion/order-coupon/result")
    CommonResult<CouponOrderResultDTO> result(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody CouponOrderCommandReqDTO body);
    @PostMapping("/internal-api/promotion/order-coupon/close-creation")
    CommonResult<CouponOrderCloseResultDTO> closeCreation(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody CouponOrderCommandReqDTO body);
    @PostMapping("/internal-api/promotion/order-coupon/close-unpaid")
    CommonResult<CouponOrderResultDTO> closeUnpaid(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody CouponOrderCommandReqDTO body);
}
