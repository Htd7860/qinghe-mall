package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Value;
import lombok.ToString;

/** 交易已登记与支付已授权分别陈述；null表示尚无本人原交易申请回执。 */
@Value @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundContinuationResultRespVO {
    Long id;
    String refundNo;
    String requestId;
    Long recordedVersion;
    Long expectedPayVersion;
    Boolean authorizationRecorded;
    Integer authorizedLimit;
}
