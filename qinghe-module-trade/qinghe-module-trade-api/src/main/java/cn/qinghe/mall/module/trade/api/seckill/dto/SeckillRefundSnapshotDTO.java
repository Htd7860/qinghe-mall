package cn.qinghe.mall.module.trade.api.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;
/** trade已确认的原退款/实物计划事实，不包含退款原因、退货地址、姓名或手机号。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillRefundSnapshotDTO {
    private Integer schemaVersion;
    private SeckillQuoteBindingRespDTO binding;
    private Long afterSaleId;
    private Long orderItemId;
    private String refundNo;
    private Integer refundedCount;
    private Integer restoreCount;
    private Long refundAmount;
    private Long payRefundId;
    private OffsetDateTime refundTime;
    private Long afterSaleVersion;
    private Long orderVersion;
    private Boolean shipped;
}
