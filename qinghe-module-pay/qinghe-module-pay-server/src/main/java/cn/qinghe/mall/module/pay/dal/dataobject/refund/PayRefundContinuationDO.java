package cn.qinghe.mall.module.pay.dal.dataobject.refund;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 一次人工原号继续授权的不可变回执；不是支付成功凭证。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayRefundContinuationDO {
    private Long tenantId;
    private Long refundId;
    private String requestId;
    private Long actorId;
    private String reason;
    private Long fromVersion;
    private Integer fromLimit;
    private Integer toLimit;
    private LocalDateTime createTime;
}
