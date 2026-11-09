package cn.qinghe.mall.module.promotion.convert.coupon;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
/** 原CouponConvert.convert(template,user)快照映射，避免新增MapStruct依赖。
 * @author 芋道源码
 */
public final class CouponConvert {
    private CouponConvert(){}
    public static CouponDO convert(CouponTemplateDO template,Long userId,Integer takeType,String grantKey,LocalDateTime now) {
        CouponDO coupon=new CouponDO();coupon.setTemplateId(template.getId());coupon.setTemplateVersion(template.getAuthVersion());
        coupon.setName(template.getName());coupon.setTakeType(takeType);coupon.setUsePrice(template.getUsePrice());
        coupon.setProductScope(template.getProductScope());coupon.setProductScopeValues(new ArrayList<>(template.getProductScopeValues()));
        coupon.setDiscountType(template.getDiscountType());coupon.setDiscountPercent(template.getDiscountPercent());
        coupon.setDiscountPrice(template.getDiscountPrice());coupon.setDiscountLimitPrice(template.getDiscountLimitPrice());
        coupon.setStatus(1);coupon.setUserId(userId);coupon.setGrantKey(grantKey);coupon.setAuthVersion(0L);
        coupon.setTenantId(template.getTenantId());coupon.setCreateTime(now);coupon.setUpdateTime(now);
        coupon.setCreator("coupon-grant");coupon.setUpdater("coupon-grant");coupon.setDeleted(false);
        if(template.getValidityType()==1){coupon.setValidStartTime(template.getValidStartTime());coupon.setValidEndTime(template.getValidEndTime());}
        else {coupon.setValidStartTime(now);coupon.setValidEndTime(now.plusDays(template.getFixedEndTerm()));}
        return coupon;
    }
}
