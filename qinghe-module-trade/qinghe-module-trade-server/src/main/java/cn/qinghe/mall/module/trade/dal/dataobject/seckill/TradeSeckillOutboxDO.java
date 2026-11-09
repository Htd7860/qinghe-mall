package cn.qinghe.mall.module.trade.dal.dataobject.seckill;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 择取现有trade事务事件字段；事件载荷只放原申请引用，不放地址或认证凭据。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class TradeSeckillOutboxDO {
    private Long tenantId;
    private String eventId;
    private String requestNo;
    private String eventType;
    private Long aggregateVersion;
    private Integer schemaVersion;
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime availableAt;
    private LocalDateTime publishedAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Integer lastErrorCode;
    private Long replayVersion;
    private LocalDateTime createdAt;
}
