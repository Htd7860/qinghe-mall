package cn.qinghe.mall.module.trade.service.aftersale.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 择取原收货/拒收命令职责；数量为本次累计实收事实，不是新增退款或库存数量。 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleInspectionCommand extends AfterSaleDecisionCommand {
    @NotNull @Min(0) @Max(9999) private Integer receivedCount;
    @NotNull @Min(0) @Max(9999) private Integer salableCount;
    /** 数量齐全且同意本次验收才为true；拒收/短少/多收均保留待核查。 */
    @NotNull private Boolean accepted;
}
