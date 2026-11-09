package cn.qinghe.mall.module.trade.dal.dataobject.order;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 单轮人工重放的操作依据及结果，不因下一轮重放覆盖历史。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class TradeOrderRecoveryReplayDO {
    private Long id;
    private Long tenantId;
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
