package cn.qinghe.mall.module.promotion.api.seckill.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/** 原退款恢复回执；RESTORED与缓存SYNCED分开，无普通商品库存前后值。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillRefundResourceReceiptDTO {
    private Long tenantId;
    private String requestNo;
    private String requestDigest;
    private Long memberId;
    private Long orderId;
    private Long afterSaleId;
    private Long orderItemId;
    private String refundNo;
    private Integer refundedCount;
    private Integer restoreCount;
    private Long refundAmount;
    private Long payRefundId;
    private OffsetDateTime refundTime;
    private Long afterSaleVersion;
    private Long orderVersion;
    private String state;
    private String cacheState;
    private Integer restoredTotal;
    private OffsetDateTime restoredTime;
}
