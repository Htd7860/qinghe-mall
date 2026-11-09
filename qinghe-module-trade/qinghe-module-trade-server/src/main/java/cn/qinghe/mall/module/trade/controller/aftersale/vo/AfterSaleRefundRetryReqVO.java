package cn.qinghe.mall.module.trade.controller.aftersale.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 原号显式重试的审计依据；不接受金额、渠道状态或提交次数。 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundRetryReqVO extends AfterSaleRefundCommandReqVO {
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedVersion;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    @NotBlank @Size(max=500) private String reason;
}
