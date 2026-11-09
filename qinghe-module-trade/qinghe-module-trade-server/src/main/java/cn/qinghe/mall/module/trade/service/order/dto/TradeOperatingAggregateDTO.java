package cn.qinghe.mall.module.trade.service.order.dto;
import lombok.Data;
import java.math.BigDecimal;

/** 固定本地账本聚合投影；SQL SUM使用DECIMAL，不用Integer承接金额汇总。 */
@Data
public class TradeOperatingAggregateDTO {
    private Long count;
    private BigDecimal amount;
    private BigDecimal refundAmount;
    private BigDecimal goodsAmount;
    private BigDecimal deliveryAmount;
}
