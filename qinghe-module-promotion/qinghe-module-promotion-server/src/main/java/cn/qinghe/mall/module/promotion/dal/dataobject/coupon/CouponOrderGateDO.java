package cn.qinghe.mall.module.promotion.dal.dataobject.coupon;

import lombok.Data;

/** 原订单唯一串行化行；关闭后永久拒绝晚到占券/核销，不随券再次使用而删除。 */
@Data
public class CouponOrderGateDO {
    private Long tenantId;
    private Long orderId;
    private Long userId;
    private Long couponId;
    private Boolean closed;
    private String reason;
}
