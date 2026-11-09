package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 持久申请原意图；只引用trade服务端报价，不在promotion复制明文地址。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class SeckillRequestDO {
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private String clientRequestId;
    private String requestDigest;
    private Long activityId;
    private Integer generation;
    private Long activityVersion;
    private Long redisEpoch;
    private String initToken;
    private Long skuId;
    private Integer quantity;
    private Integer priceCent;
    private Long goodsAmount;
    private Long shippingAmount;
    private Long payAmount;
    private String quoteId;
    private String quoteDigest;
    private LocalDateTime quoteExpiresTime;
    private String reservationToken;
    private Integer status;
    private Long orderId;
    private Long fenceVersion;
    private LocalDateTime acceptedTime;
    private LocalDateTime reconcileDeadline;
    private LocalDateTime payDeadline;
    private LocalDateTime nextRetryTime;
    private Integer retryCount;
    private String claimToken;
    private LocalDateTime lockedUntil;
    private Integer failureCode;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String resourceState;
    private Long resourceOrderVersion;
    private Long resourcePayOrderId;
    private LocalDateTime resourceTime;
    private String resourceCacheState;
    private Integer resourceCheckAttempts;
    private LocalDateTime nextResourceCheckAt;
}
