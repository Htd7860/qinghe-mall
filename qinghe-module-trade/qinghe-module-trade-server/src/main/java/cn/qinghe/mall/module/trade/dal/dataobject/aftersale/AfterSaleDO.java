package cn.qinghe.mall.module.trade.dal.dataobject.aftersale;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 择取原售后申请字段，商品快照沿原orderItem读取；金额是申请占用，非退款到账。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleDO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String requestId;
    private String no;
    private Long orderId;
    private Long orderItemId;
    private Long orderVersion;
    private Integer status;
    private Integer way;
    private Integer count;
    private Long goodsRefundPrice;
    private Long originalDeliveryShare;
    private String applyReason;
    private String applyDescription;
    private Long authVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private String auditReason;
    private Boolean qualityApproved;
    private LocalDateTime returnDeadline;
    private String returnAddress;
    private Long logisticsId;
    private String logisticsCode;
    private String logisticsName;
    private String logisticsNo;
    private LocalDateTime deliveryTime;
    /** 首次记录验货的时间；后续核查更新不改写，各次决定时间保存在不可变日志。 */
    private LocalDateTime receiveTime;
    private Long receiveUserId;
    private Integer receivedCount;
    private Integer salableCount;
    private String receiveReason;
    /** 资金确认时间，不等于库存/优惠券已恢复。 */
    private LocalDateTime refundTime;
}
