package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Value;

/** 原准备回执，重试仍返原计划；不表示渠道已调用、到账、库存或优惠券恢复。 */
@Value
public class AfterSaleRefundPlanDTO {
    Long id;
    String refundNo;
    Long authVersion;
    Long goodsPrice;
    Long deliveryPrice;
    Long totalPrice;
    Integer restoreCount;
    Boolean couponReturnRequired;
}
