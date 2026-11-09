package cn.qinghe.mall.module.promotion.api.seckill.dto;

import lombok.Data;

/** 只引用原申请/订单/售后，金额及回补数由原成功退款重新读取。 */
@Data
public class SeckillRefundResourceLookupDTO {
    private String requestNo;
    private String requestDigest;
    private Long memberId;
    private Long orderId;
    private Long afterSaleId;
}
