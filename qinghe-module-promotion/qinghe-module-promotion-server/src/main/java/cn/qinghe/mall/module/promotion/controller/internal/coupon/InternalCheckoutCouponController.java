package cn.qinghe.mall.module.promotion.controller.internal.coupon;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.promotion.api.coupon.dto.CouponRespDTO;
import cn.qinghe.mall.module.promotion.service.coupon.AppCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.*;
/** 原CouponApi读取职责，最小本人选中券快照；不公开核销/退回权限。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor
public class InternalCheckoutCouponController {
    private final AppCouponService coupons;
    @GetMapping("/internal-api/promotion/coupon/current")
    public CommonResult<CouponRespDTO> get(@RequestParam @Min(1) Long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");CouponRespDTO row=coupons.getForCheckout(id);
        return row==null?CommonResult.error(404,"优惠券不存在"):CommonResult.success(row);
    }
}
