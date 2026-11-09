package cn.qinghe.mall.module.pay.api.refund.dto;
import lombok.Value;
import lombok.ToString;

/** 原UUID已提交授权回执；机会可能已消费，不等于当前可再次提交或退款成功。 */
@Value @ToString(onlyExplicitlyIncluded=true)
public class PayRefundContinuationResultDTO {
    Long refundId;
    String no;
    String requestId;
    Long fromVersion;
    Integer fromLimit;
    Integer toLimit;
}
