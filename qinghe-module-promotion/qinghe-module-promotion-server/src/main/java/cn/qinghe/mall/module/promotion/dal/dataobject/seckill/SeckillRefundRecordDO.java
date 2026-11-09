package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 原成功退款的库存义务；PENDING不代表已经回补，不保存用户退货地址或原因。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillRefundRecordDO {
    private Long tenantId;
    private Long afterSaleId;
    private String requestNo;
    private Long orderId;
    private Long orderItemId;
    private Long memberId;
    private Long activityId;
    private Integer generation;
    private Long redisEpoch;
    private Long skuId;
    private String refundNo;
    private Integer refundedCount;
    private Integer restoreCount;
    private Long refundAmount;
    private Long payRefundId;
    private LocalDateTime refundTime;
    private Long afterSaleVersion;
    private Long orderVersion;
    private Boolean shipped;
    private String state;
    private Integer restoredTotal;
    private LocalDateTime restoredTime;
    private String cacheState;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
