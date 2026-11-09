package cn.qinghe.mall.module.trade.service.order.dto;
import lombok.Data;
/** 内部支付绑定结果；requiresPayment仅表示仍需付款，不代表已配置渠道或授予付款权限。 */
@Data
public class TradeOrderPaymentResult {
    private Long orderId;
    private Long payOrderId;
    private Boolean payStatus;
    private Boolean requiresPayment;
}
