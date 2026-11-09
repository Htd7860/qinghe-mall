package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Value;

/** 申请受理回执，不是退款到账；运费审核和资金执行尚未接入。 */
@Value
public class AfterSaleApplyResult {
    Long id;
    String no;
    Integer status;
    Integer count;
    Long goodsRefundPrice;
}
