package cn.qinghe.mall.module.trade.dal.dataobject.order;
import lombok.Data;
import java.time.LocalDateTime;
/** 成功报价回执；过期且未绑定订单时清PII快照，保留轻量请求号避免原请求被续期重建。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class TradeSettlementQuoteDO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String quoteId;
    private String requestId;
    private String requestDigest;
    private Long cartVersion;
    private Long addressId;
    private Long couponId;
    private String snapshot;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Long boundOrderId;
}
