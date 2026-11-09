package cn.qinghe.mall.module.trade.dal.dataobject.order;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 七个固定恢复动作；订单编号保持原义，REFUND另绑不可变原售后编号。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class TradeOrderRecoveryDO {
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long afterSaleId;
    private String kind;
    private String status;
    private Integer attempts;
    private LocalDateTime availableAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Integer lastErrorCode;
    private Long lastJobId;
    private String traceId;
    private String activeReplayId;
    private Long authVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
