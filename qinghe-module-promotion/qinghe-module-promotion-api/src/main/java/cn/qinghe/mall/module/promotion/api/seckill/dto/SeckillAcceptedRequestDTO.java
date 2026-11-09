package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;

/** 已持久受理的原事实与当前申请状态；不含地址/缓存token/服务凭据，不允许接浏览器回写。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillAcceptedRequestDTO {
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private String clientRequestId;
    private String requestDigest;
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
    private String quoteId;
    private String quoteDigest;
    private OffsetDateTime quoteExpiresAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime reconcileDeadline;
    private OffsetDateTime payDeadline;
    private Integer status;
    private Long orderId;
    private Long fenceVersion;
    private Long version;
}
