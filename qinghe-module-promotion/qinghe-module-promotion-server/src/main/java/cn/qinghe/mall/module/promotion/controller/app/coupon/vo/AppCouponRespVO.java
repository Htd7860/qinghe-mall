package cn.qinghe.mall.module.promotion.controller.app.coupon.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 直接读取已发券快照，模板暂停/修改不重写本人券。 @author 芋道源码 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppCouponRespVO extends AppCouponRulesRespVO {
    private Long templateId;
    private Integer status;
}
