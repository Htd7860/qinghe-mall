package cn.qinghe.mall.module.trade.api.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;
/** 来源库原结果事件/永久栅栏及订单时间证明，不返回地址或认证凭据。 */
@Data
public class SeckillOrderResultSnapshotDTO {
    private Long tenantId;
    private String eventId;
    private String requestNo;
    private String eventType;
    private Long aggregateVersion;
    private Integer schemaVersion;
    private Long occurredAt;
    private SeckillQuoteBindingRespDTO binding;
    private OffsetDateTime orderCreatedAt;
    private OffsetDateTime payDeadline;
}
