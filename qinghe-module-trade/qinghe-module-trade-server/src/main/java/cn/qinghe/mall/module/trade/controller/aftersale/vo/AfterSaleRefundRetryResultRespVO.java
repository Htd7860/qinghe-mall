package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Value;
import lombok.ToString;

/** 原重试登记回执，不表示渠道已调用或退款到账；不返回原因、操作者或原日志详情。 */
@Value @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundRetryResultRespVO {
    Long id;
    String refundNo;
    String requestId;
    Long recordedVersion;
}
