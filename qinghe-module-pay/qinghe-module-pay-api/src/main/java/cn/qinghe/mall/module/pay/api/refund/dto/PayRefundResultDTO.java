package cn.qinghe.mall.module.pay.api.refund.dto;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 原PayRefundRespDTO账本结果职责；不暴露渠道配置/流水、原SDK数据或私密原因。
 * PREPARED/PROCESSING/UNKNOWN/FAILED均不能当作退款到账。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class PayRefundResultDTO {
    private Long id;
    private Long tenantId;
    private String appKey;
    private Long orderId;
    private Long userId;
    private Integer userType;
    private String merchantOrderId;
    private String merchantRefundId;
    private String no;
    private Long payPrice;
    private Long refundPrice;
    private String status;
    private LocalDateTime successTime;
    private Long authVersion;
    private Integer submitCount;
    /** 默认3，耗尽后只有原号人工授权每次增加1；历史次数不清零。 */
    private Integer submissionLimit;
    /** 原号显式重试最早时间；首次尚未提交或已授权机会用尽时为空。 */
    private LocalDateTime retryAfter;
}
