package cn.qinghe.mall.module.pay.api.order.dto;

import lombok.Data;
import lombok.ToString;

/** 择取原PayOrderSubmitRespVO的状态/展示内容；原支付账供trade核对，无渠道密钥或原始报文。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayOrderSubmissionRespDTO {
    private PayOrderRespDTO payment;
    private String state;
    private String qrCode;
    private Integer qrRecoveryCount;
    private java.time.LocalDateTime qrRecoveryAfter;
}
