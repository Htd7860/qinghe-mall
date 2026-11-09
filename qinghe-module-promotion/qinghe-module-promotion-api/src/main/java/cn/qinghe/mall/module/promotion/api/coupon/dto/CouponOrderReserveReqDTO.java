package cn.qinghe.mall.module.promotion.api.coupon.dto;

import lombok.Data;
import java.util.List;

/** 订单准备记录的原商品事实和分摊；服务端核价必须与 expectedQuote 完全一致才占券。 */
@Data
public class CouponOrderReserveReqDTO {
    private Long orderId;
    private Long userId;
    private Long couponId;
    private List<CouponOrderLineDTO> lines;
    private CouponQuoteDTO expectedQuote;
}
