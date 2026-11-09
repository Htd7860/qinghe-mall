package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** READY只表示已排队；后续结果来自这一轮的历史记录，不借用其他重放轮次结果。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class TradeRecoveryReplayRespVO {
    private Long recoveryId;
    private Long orderId;
    private String kind;
    private String requestId;
    private Long actorId;
    private String reason;
    private Long fromVersion;
    private Long toVersion;
    private Integer previousAttempts;
    private Integer previousErrorCode;
    private String status;
    private Integer attempts;
    private Integer lastErrorCode;
    private Long lastJobId;
    private String traceId;
    private Long lastVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
