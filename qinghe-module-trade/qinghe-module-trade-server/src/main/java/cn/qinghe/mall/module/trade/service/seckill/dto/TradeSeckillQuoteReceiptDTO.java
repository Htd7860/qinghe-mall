package cn.qinghe.mall.module.trade.service.seckill.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/** 报价结果白名单，不输出密文、密钥标识、联系人、电话或地址明文。不是订单或已受理凭证。 */
@Data
public class TradeSeckillQuoteReceiptDTO {
    private String quoteId;
    private String requestId;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private Boolean expired;
    private Boolean snapshotPresent;
    private Boolean bound;
    private Long serverTime;
}
