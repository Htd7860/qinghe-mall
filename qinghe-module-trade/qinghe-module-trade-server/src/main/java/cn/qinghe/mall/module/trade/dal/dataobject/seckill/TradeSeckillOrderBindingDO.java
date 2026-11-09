package cn.qinghe.mall.module.trade.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 演化原V002的申请唯一建单栅栏；关闭先到保留原引用，不能重新打开。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class TradeSeckillOrderBindingDO {
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private String clientRequestId;
    private String requestDigest;
    private String quoteId;
    private String quoteDigest;
    private Long activityId;
    private Integer generation;
    private Long activityVersion;
    private Long redisEpoch;
    private Long skuId;
    private Integer quantity;
    private Integer priceCent;
    private Long goodsAmount;
    private Long shippingAmount;
    private Long payAmount;
    private LocalDateTime quoteExpiresAt;
    private Long orderId;
    private Integer state;
    private Long fenceVersion;
    private String closeCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private Integer cleanupAttempts;
    private LocalDateTime nextCleanupAt;
}
