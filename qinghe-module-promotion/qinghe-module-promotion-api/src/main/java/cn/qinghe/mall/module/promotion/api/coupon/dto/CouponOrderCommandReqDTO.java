package cn.qinghe.mall.module.promotion.api.coupon.dto;

import lombok.Data;

/** 仅可信 trade 发送；不提供外部状态、退券原因或目标租户字段。 */
@Data
public class CouponOrderCommandReqDTO {
    private Long orderId;
    private Long userId;
    private Long couponId;
}
