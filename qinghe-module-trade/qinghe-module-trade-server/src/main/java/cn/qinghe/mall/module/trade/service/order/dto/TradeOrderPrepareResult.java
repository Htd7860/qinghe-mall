package cn.qinghe.mall.module.trade.service.order.dto;

import lombok.Data;

/** 仅准备进度，不返回支付单或可付款标志；正式订单创建后应另查主订单。 */
@Data
public class TradeOrderPrepareResult {
    private Long preparationId;
    private String requestId;
    private String quoteId;
    private String no;
    private String stage;
}
