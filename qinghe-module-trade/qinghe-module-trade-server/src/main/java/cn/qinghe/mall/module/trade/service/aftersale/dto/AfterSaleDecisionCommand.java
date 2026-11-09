package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;
import javax.validation.Valid;

/** 固定售后决定命令；操作者/租户/目标状态由可信入口确定，不接受客户端传入。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleDecisionCommand {
    @NotNull @Min(1) @Max(9007199254740991L) private Long id;
    @NotNull @Min(0) @Max(9007199254740991L) private Long expectedVersion;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    @NotBlank @Size(max=500) private String reason;
    /** 仅审核同意时可为true，必须有理由；不直接授权运费或执行退款。 */
    @NotNull private Boolean qualityApproved=false;
    /** 仅同意退货退款时填写，作为原申请寄回地址快照。 */
    @Valid private AfterSaleReturnAddress returnAddress;
}
