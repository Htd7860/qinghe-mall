package cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template;
import lombok.Data;
import javax.validation.constraints.*;
/** 原启停参数增加当前版本。
 * @author 芋道源码
 */
@Data
public class CouponTemplateUpdateStatusReqVO {
    @NotNull @Min(1) private Long id;
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull @Min(0) @Max(1) private Integer status;
}
