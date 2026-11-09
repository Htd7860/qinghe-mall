package cn.qinghe.mall.module.trade.api.seckill.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/** 服务间原绑定回执；0报价已绑定/10已建订单/20建单关闭。不是promotion库存受理成功。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillQuoteBindingRespDTO {
    private Long tenantId;
    private Long memberId;
    private String requestNo;
    private String clientRequestId;
    private String requestDigest;
    private String quoteId;
    private String quoteDigest;
    private Integer state;
    private Long fenceVersion;
    private Long orderId;
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
    private OffsetDateTime quoteExpiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime closedAt;
}
