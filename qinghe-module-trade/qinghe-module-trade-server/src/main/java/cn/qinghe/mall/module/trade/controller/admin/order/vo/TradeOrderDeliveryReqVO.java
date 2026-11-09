package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 择取原发货VO；当前仅一次快递，不提供无需发货/多包裹。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderDeliveryReqVO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long id;
    @NotNull @Min(1) @Max(9007199254740991L) private Long logisticsId;
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9-]{3,63}") private String logisticsNo;
    @NotNull @Min(0) @Max(9007199254740991L) private Long expectedVersion;
}
