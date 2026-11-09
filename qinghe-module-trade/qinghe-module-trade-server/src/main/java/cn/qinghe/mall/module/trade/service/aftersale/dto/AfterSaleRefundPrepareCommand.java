package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 财务准备命令不接收金额、退款号、质量结论或恢复数量。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundPrepareCommand {
    @NotNull @Min(1) @Max(9007199254740991L) private Long id;
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedVersion;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    @NotBlank @Size(max=500) private String reason;
}
