package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import java.time.LocalDateTime;

/** 只公开事件定位/投递/消费进度；不返回原payload、租约owner或业务隐私。 */
@Data
public class TradeEventRespVO {
    private String eventId;
    private Long orderId;
    private String eventType;
    private Long aggregateVersion;
    private Integer schemaVersion;
    private String status;
    private Integer attempts;
    private Long replayVersion;
    private Integer lastErrorCode;
    private LocalDateTime createdAt;
    private LocalDateTime availableAt;
    private LocalDateTime publishedAt;
    private LocalDateTime leaseUntil;
    private LocalDateTime consumedAt;
    private Integer taskCount;
}
