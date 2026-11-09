package cn.qinghe.mall.module.trade.controller.aftersale.vo;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 只引用已经提交的原退款计划，不接受金额、渠道状态或资源恢复数量。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundCommandReqVO {
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long id;
    @NotBlank @Pattern(regexp="RF[0-9a-f]{32}")
    private String refundNo;
}
