package cn.qinghe.mall.module.promotion.service.coupon;
/** 原takeCoupon的单个成功发放内核。不是HTTP授权入口。
 * 调用者先验证实际店铺/可信会员（营销还须复查退出、批次状态），再在promotion READ_COMMITTED本地写事务中调用。
 * 不能把浏览器的tenantId/userId/takeType直接传入；AppCouponService已接手动领取前置；营销调用者仍须实现批次/退出复查。
 * @author 芋道源码
 */
public interface CouponService {
    Long takeCoupon(Long templateId,Long trustedUserId,Integer trustedTakeType,String stableGrantKey);
    Long findGrantResult(Long trustedUserId,String stableGrantKey);
}
