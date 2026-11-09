package cn.qinghe.mall.module.promotion.api.coupon.dto;

import lombok.Data;

/** 创建失败的持久关闭回执；从未占券时 state 为 null，不伪造核价或核销记录。 */
@Data
public class CouponOrderCloseResultDTO {
    private Long orderId;
    private Long couponId;
    private Boolean closed;
    private Integer state;
    private String reason;
}
