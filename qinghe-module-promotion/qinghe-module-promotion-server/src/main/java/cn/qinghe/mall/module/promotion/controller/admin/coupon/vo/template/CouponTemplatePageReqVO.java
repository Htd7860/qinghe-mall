package cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
/** 原模板分页筛选按当前范围择取；不开放无界导出/任意租户条件。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class CouponTemplatePageReqVO extends PageParam {
    @Size(max=64) private String name;
    @Min(0) @Max(1) private Integer status;
    @Min(1) @Max(2) private Integer takeType;
    @Min(1) @Max(2) private Integer discountType;
}
