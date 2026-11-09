package cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * 统一 退款 Request DTO
 *
 * @author jason
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@lombok.ToString(onlyExplicitlyIncluded=true)
public class PayRefundUnifiedReqDTO {

    /**
     * 外部订单号
     *
     * 对应 PayOrderExtensionDO 的 no 字段
     */
    @NotEmpty(message = "外部订单编号不能为空")
    private String outTradeNo;

    /**
     * 外部退款号
     *
     * 对应 PayRefundDO 的 no 字段
     */
    @NotEmpty(message = "退款请求单号不能为空")
    private String outRefundNo;

    /**
     * 退款原因
     */
    @NotEmpty(message = "退款原因不能为空")
    @org.hibernate.validator.constraints.Length(max=200)
    private String reason;

    /**
     * 支付金额，单位：分
     *
     * 目前微信支付在退款的时候，必须传递该字段
     */
    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    private Long payPrice;
    /**
     * 退款金额，单位：分
     */
    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    private Long refundPrice;

    /**
     * 退款结果的 notify 回调地址；当前支付宝同步退款不使用，未接回调时留空。
     */
    @URL(message = "支付结果的 notify 回调地址必须是 URL 格式")
    private String notifyUrl;

}
