package cn.qinghe.mall.module.trade.dal.dataobject.order;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 原事务事件不变；领取/发送结果单独更新，不删除未知或人工记录。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderOutboxDO {
    private String eventId;
    private Long tenantId;
    private Long preparationId;
    private String eventType;
    private Long aggregateVersion;
    private Integer schemaVersion;
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime createdAt;
    private LocalDateTime availableAt;
    private LocalDateTime publishedAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Integer lastErrorCode;
    /** 仅人工重放代次；发布过程不改此版本。 */
    private Long replayVersion;
}
