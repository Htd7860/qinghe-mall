package cn.qinghe.mall.module.pay.api.refund.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 原createRefund的原单/退款金额与原因职责；金额均由原交易退款计划提供。
 * @author 芋道源码
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class PayRefundCreateReqDTO extends PayRefundGetReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long payPrice;
    @NotNull @Min(1) @Max(9007199254740991L) private Long refundPrice;
    @NotBlank @Size(max = 200) private String reason;
}
