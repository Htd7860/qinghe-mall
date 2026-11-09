package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 择取原AppAfterSaleDeliveryReqVO的承运商/单号，复用固定命令回执。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleReturnDeliveryCommand extends AfterSaleDecisionCommand {
    @NotNull @Min(1) @Max(9007199254740991L) private Long logisticsId;
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9-]{3,63}") private String logisticsNo;
    public AfterSaleReturnDeliveryCommand(){setReason("买家提交退货物流");}
}
