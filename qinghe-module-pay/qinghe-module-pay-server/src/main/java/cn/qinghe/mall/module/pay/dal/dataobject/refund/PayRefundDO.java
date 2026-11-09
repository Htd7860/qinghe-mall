package cn.qinghe.mall.module.pay.dal.dataobject.refund;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 支付退款单，择取原PayRefundDO稳定业务/渠道归属、金额与结果字段。
 * 一个支付单可分次退款；所有非成功记录保留原号及额度，不删除资金记录。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class PayRefundDO {
    private Long id;
    private Long tenantId;
    private Long appId;
    private Long channelId;
    private Long channelVersion;
    private Long orderId;
    private String orderNo;
    private String channelOrderNo;
    private Long userId;
    private Integer userType;
    private String merchantOrderId;
    private String merchantRefundId;
    /** 与原交易退款计划相同的RF编号，首次登记后不可变。 */
    private String no;
    private String status;
    private Long payPrice;
    private Long refundPrice;
    private String reason;
    private String channelRefundNo;
    private LocalDateTime successTime;
    private LocalDateTime submittedTime;
    private LocalDateTime lastSubmitTime;
    private Integer submitCount;
    private Integer submissionLimit;
    private Long authVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
