package cn.qinghe.mall.module.promotion.controller.internal.coupon;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import cn.qinghe.mall.module.promotion.service.coupon.CouponOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原 CouponApiImpl 委托；创建/未付关闭/原全额退款只接受trade服务，不接受浏览器退券/作废命令。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalOrderCouponController {
    public static final String RESERVE = "/internal-api/promotion/order-coupon/reserve";
    public static final String USE = "/internal-api/promotion/order-coupon/use";
    public static final String RESULT = "/internal-api/promotion/order-coupon/result";
    public static final String CLOSE_CREATION = "/internal-api/promotion/order-coupon/close-creation";
    public static final String CLOSE_UNPAID = "/internal-api/promotion/order-coupon/close-unpaid";
    public static final String RETURN_REFUND = "/internal-api/promotion/order-coupon/return-refund";
    private final CouponOrderService coupons;
    /** trade仅对原整单全额退款调用，沿原券/原截止时间，不接外部自由退券原因。 */
    @PostMapping(RETURN_REFUND)
    public CommonResult<CouponOrderResultDTO> returnRefund(@RequestBody CouponOrderCommandReqDTO body) {
        if(body==null)throw exception0(400,"缺少原订单身份");
        return CommonResult.success(coupons.returnUsed(body.getUserId(),body.getOrderId(),body.getCouponId(),"FULL_REFUND"));
    }

    @PostMapping(RESERVE)
    public CommonResult<CouponOrderResultDTO> reserve(@RequestBody CouponOrderReserveReqDTO body) {
        if (body == null || body.getExpectedQuote() == null) {
            throw exception0(400, "缺少原订单优惠分摊");
        }
        return CommonResult.success(coupons.reserve(body.getUserId(), body.getOrderId(), body.getCouponId(),
                body.getLines(), true, body.getExpectedQuote()));
    }

    @PostMapping(USE)
    public CommonResult<CouponOrderResultDTO> use(@RequestBody CouponOrderCommandReqDTO body) {
        if (body == null) throw exception0(400, "缺少原订单身份");
        return CommonResult.success(coupons.use(body.getUserId(), body.getOrderId(), body.getCouponId()));
    }

    @PostMapping(RESULT)
    public CommonResult<CouponOrderResultDTO> result(@RequestBody CouponOrderCommandReqDTO body) {
        if (body == null) throw exception0(400, "缺少原订单身份");
        CouponOrderResultDTO result = coupons.find(body.getUserId(), body.getOrderId());
        if (result != null && !java.util.Objects.equals(body.getCouponId(), result.getCouponId())) {
            throw exception0(409, "原订单优惠券不一致");
        }
        return CommonResult.success(result);
    }

    @PostMapping(CLOSE_CREATION)
    public CommonResult<CouponOrderCloseResultDTO> closeCreation(@RequestBody CouponOrderCommandReqDTO body) {
        if(body==null)throw exception0(400,"缺少原订单身份");
        return CommonResult.success(coupons.closeCreation(body.getUserId(),body.getOrderId(),body.getCouponId()));
    }

    /** 正式订单已完成核销，只退本订单原券；过期/作废事实由现有领域方法保留。 */
    @PostMapping(CLOSE_UNPAID)
    public CommonResult<CouponOrderResultDTO> closeUnpaid(@RequestBody CouponOrderCommandReqDTO body) {
        if(body==null)throw exception0(400,"缺少原订单身份");
        return CommonResult.success(coupons.returnUsed(body.getUserId(),body.getOrderId(),body.getCouponId(),"UNPAID_CLOSED"));
    }
}
