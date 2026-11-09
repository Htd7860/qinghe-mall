package cn.qinghe.mall.module.pay.api.refund.dto;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 原业务身份查询；仅可信trade服务构造，不能直接绑定消费者/财务HTTP输入。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class PayRefundGetReqDTO {
    @NotBlank @Pattern(regexp = "qinghe-trade") private String appKey;
    @NotNull @Min(1) @Max(9007199254740991L) private Long userId;
    /** 支付单编号；merchantOrderId才是原交易订单编号。 */
    @NotNull @Min(1) @Max(9007199254740991L) private Long orderId;
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,15}") private String merchantOrderId;
    /** 原售后编号，不与渠道退款号混用；一个售后不能换号再退款。 */
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,15}") private String merchantRefundId;
    @NotBlank @Pattern(regexp = "RF[0-9a-f]{32}") private String no;
}
