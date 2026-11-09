package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Value;
import lombok.ToString;

/** 支付账本只读预算；不是人工回执或渠道成功凭证。 */
@Value @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundBudgetRespVO {
    Long id;
    String refundNo;
    Long payVersion;
    Integer submitCount;
    Integer submissionLimit;
    String status;
    Long retryAfter;
}
