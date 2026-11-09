package cn.qinghe.mall.module.trade.api.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;

/** 原绑定和真实订单当前状态；不是支付平台回执，也不授权调用方直接释放资源。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillOrderStateDTO {
    private SeckillQuoteBindingRespDTO binding;
    private Long orderVersion;
    private Integer orderStatus;
    private Boolean payStatus;
    private Long payOrderId;
    private OffsetDateTime orderCreatedAt;
    private OffsetDateTime payDeadline;
    private OffsetDateTime payTime;
    private OffsetDateTime cancelTime;
    private Integer cancelType;
    private Boolean cancelResourcesReleased;
    private Long refundPrice;
}
