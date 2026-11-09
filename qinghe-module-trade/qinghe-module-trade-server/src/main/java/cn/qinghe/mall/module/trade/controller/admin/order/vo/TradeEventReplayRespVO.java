package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 原人工申请的不可变排队回执；不是Broker投递/业务执行完成证明。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeEventReplayRespVO {
    private String requestId;
    private String eventId;
    private Long actorId;
    private String reason;
    private Long fromVersion;
    private Long toVersion;
    private String previousStatus;
    private Integer previousAttempts;
    private Integer previousErrorCode;
    private LocalDateTime previousPublishedAt;
    private LocalDateTime createTime;
}
