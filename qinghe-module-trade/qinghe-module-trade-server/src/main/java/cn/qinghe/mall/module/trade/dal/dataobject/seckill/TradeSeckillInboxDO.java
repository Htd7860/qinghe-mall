package cn.qinghe.mall.module.trade.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 只写最终消费结果；订单/关闭栅栏/结果事件与此记录同事务。 */
@Data
public class TradeSeckillInboxDO {
    private Long tenantId;
    private String eventId;
    private String requestNo;
    private Long aggregateVersion;
    private String eventType;
    private String payloadHash;
    private Integer resultState;
    private Long orderId;
    private String resultEventId;
    private LocalDateTime processedAt;
}
