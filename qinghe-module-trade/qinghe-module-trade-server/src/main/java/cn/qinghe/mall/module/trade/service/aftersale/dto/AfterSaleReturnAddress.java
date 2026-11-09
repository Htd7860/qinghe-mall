package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 由当前有审核权限的商家为本次退货提供，不使用消费者原收货地址兜底。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleReturnAddress {
    @NotBlank @Size(max=50) private String name;
    @NotBlank @Pattern(regexp="1[3-9][0-9]{9}") private String mobile;
    @NotNull @Min(1) private Integer areaId;
    @NotBlank @Size(max=255) private String detail;
}
