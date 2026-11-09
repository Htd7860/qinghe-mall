package cn.qinghe.mall.module.trade.service.order.dto;
import lombok.Data;
import lombok.ToString;

/** 既有订单日志中的快递变更快照，不记录收件地址/电话或输出控制台。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderDeliveryLogDTO {
    private Long previousLogisticsId;
    private String previousLogisticsName;
    private String previousLogisticsNo;
    private Long logisticsId;
    private String logisticsName;
    private String logisticsNo;
    private String reason;
}
