package cn.qinghe.mall.module.promotion.service.coupon;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
/** 原优惠券模板CRUD职责；状态只控制新发放，不改已发快照。
 * @author 芋道源码
 */
public interface CouponTemplateService {
    Long createCouponTemplate(CouponTemplateBaseVO body);
    void updateCouponTemplate(Long id,Long expectedVersion,CouponTemplateBaseVO body);
    void updateCouponTemplateStatus(Long id,Long expectedVersion,Integer status);
    void deleteCouponTemplate(Long id,Long expectedVersion);
    CouponTemplateDO getCouponTemplate(Long id);
    PageResult<CouponTemplateDO> getCouponTemplatePage(CouponTemplatePageReqVO body);
}
