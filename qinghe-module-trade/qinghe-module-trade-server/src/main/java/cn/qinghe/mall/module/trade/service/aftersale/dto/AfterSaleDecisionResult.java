package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Value;

/** 原决定回执；重试返回原决定的状态/版本，当前状态需另行查询。 */
@Value
public class AfterSaleDecisionResult {
    Long id;
    Integer status;
    Long authVersion;
}
