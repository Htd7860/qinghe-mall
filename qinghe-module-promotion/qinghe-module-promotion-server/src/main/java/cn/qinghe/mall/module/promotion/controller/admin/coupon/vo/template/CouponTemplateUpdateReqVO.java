package cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
/** 原模板修改参数增加当前版本；不接状态/统计/归属。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class CouponTemplateUpdateReqVO extends CouponTemplateBaseVO {
    @NotNull @Min(1) private Long id;
    @NotNull @Min(0) private Long expectedVersion;
}
