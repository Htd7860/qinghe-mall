package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import java.time.LocalDateTime;

/** 最小经营诊断投影，不返回租约凭据或会员收件信息。 */
@Data
public class TradeRecoveryRespVO {
    private Long id;
    private Long orderId;
    private Long afterSaleId;
    private String kind;
    private String status;
    private Integer attempts;
    private LocalDateTime availableAt;
    private Integer lastErrorCode;
    private Long lastJobId;
    private String traceId;
    private String activeReplayId;
    private Long authVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
