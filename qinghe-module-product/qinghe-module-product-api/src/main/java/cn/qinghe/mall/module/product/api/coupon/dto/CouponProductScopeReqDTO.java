package cn.qinghe.mall.module.product.api.coupon.dto;
import lombok.Data;
import javax.validation.constraints.*;
import java.util.List;
/** 原ProductSpuApi/ProductCategoryApi.validateList的批量存在/归属校验最小请求。
 * @author 芋道源码
 */
@Data
public class CouponProductScopeReqDTO {
    @NotNull @Min(2) @Max(3) private Integer productScope;
    @NotEmpty @Size(max=200) private List<@NotNull @Min(1) Long> ids;
}
