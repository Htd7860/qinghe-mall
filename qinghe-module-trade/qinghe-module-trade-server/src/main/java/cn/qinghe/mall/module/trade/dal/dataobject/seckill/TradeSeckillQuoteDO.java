package cn.qinghe.mall.module.trade.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 沿用trade报价原请求回执；秒杀与普通购物车报价分表，地址等快照仅存密文。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class TradeSeckillQuoteDO {
    private Long tenantId;
    private Long memberId;
    private String quoteId;
    private String requestId;
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
    private String quoteDigest;
    private String snapshotCipher;
    private String keyId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String boundRequestNo;
    private Long boundOrderId;
}
