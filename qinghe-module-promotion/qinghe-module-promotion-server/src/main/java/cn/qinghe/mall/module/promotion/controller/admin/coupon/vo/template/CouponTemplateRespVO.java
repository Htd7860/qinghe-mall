package cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 原模板展示参数的最小投影，不对外返回租户/审计操作者或内部DO。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class CouponTemplateRespVO extends CouponTemplateBaseVO {
    private Long id;
    private Integer status;
    private Integer takeCount;
    private Long authVersion;
}
