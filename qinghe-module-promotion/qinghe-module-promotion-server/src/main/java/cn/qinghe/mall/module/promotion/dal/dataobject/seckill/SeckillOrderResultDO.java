package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import java.time.LocalDateTime;
/** 结果处理最终回执；关闭后的Redis释放进度独立于已提交的数据库账本。 */
@Data
public class SeckillOrderResultDO {
    private Long tenantId;
    private String eventId;
    private String requestNo;
    private Long aggregateVersion;
    private String eventType;
    private String payloadHash;
    private Integer resultState;
    private Long orderId;
    private Long requestVersion;
    private String cacheSyncState;
    private LocalDateTime cacheSyncedAt;
    private LocalDateTime processedAt;
}
