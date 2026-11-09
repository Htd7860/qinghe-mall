package cn.qinghe.mall.module.trade.dal.dataobject.order;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** B06 创建准备记录；id 作为后续同一订单的稳定编号，尚非可付款主订单。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class TradeOrderPrepareDO {
    private Long id;
    /** 0 普通购物车订单；1 秒杀。秒杀没有购物车项。 */
    private Integer sourceType;
    private Long tenantId;
    private Long userId;
    private String requestId;
    private String requestHash;
    private String quoteId;
    private String no;
    private String stage;
    private Long authVersion;
    private String remark;
    private String snapshot;
    private Integer productCount;
    private Long totalPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private LocalDateTime prepareDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
