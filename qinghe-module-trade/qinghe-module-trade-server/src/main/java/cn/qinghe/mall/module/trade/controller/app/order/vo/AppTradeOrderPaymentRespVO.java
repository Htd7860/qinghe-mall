package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import lombok.ToString;
/** 原支付提交展示职责；二维码仅在原单可付款时返回，不能当作付款成功凭据。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AppTradeOrderPaymentRespVO {
    private AppTradeOrderPageItemRespVO order;
    private String paymentState;
    private Boolean canSubmit;
    private String qrCode;
    private Boolean canRecoverQr;
    private Integer qrRecoveryCount;
    private Long qrRecoveryAfter;
}
