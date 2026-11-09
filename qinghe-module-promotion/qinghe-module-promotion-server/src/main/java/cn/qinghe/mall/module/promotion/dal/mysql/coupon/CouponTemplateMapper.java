package cn.qinghe.mall.module.promotion.dal.mysql.coupon;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template.CouponTemplatePageReqVO;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import org.apache.ibatis.annotations.*;
/** 原模板分页/额度条件更新职责；完整增量额度条件与模板父锁为B05适配。
 * @author 芋道源码
 */
@Mapper
public interface CouponTemplateMapper extends BaseMapperX<CouponTemplateDO> {
    default Long tenant() {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        if(tenant<=0 || TenantContextHolder.isIgnore()) throw new IllegalStateException("优惠券操作需要可信租户");
        return tenant;
    }
    default CouponTemplateDO selectForUpdate(Long id) {
        MybatisUtils.requireWriteTransaction();
        return selectOne(new LambdaQueryWrapperX<CouponTemplateDO>().eq(CouponTemplateDO::getTenantId,tenant())
                .eq(CouponTemplateDO::getId,id).last("FOR UPDATE"));
    }
    default PageResult<CouponTemplateDO> selectPage(CouponTemplatePageReqVO body) {
        return selectPage(body,new LambdaQueryWrapperX<CouponTemplateDO>().eq(CouponTemplateDO::getTenantId,tenant())
                .likeIfPresent(CouponTemplateDO::getName,body.getName()).eqIfPresent(CouponTemplateDO::getStatus,body.getStatus())
                .eqIfPresent(CouponTemplateDO::getTakeType,body.getTakeType()).eqIfPresent(CouponTemplateDO::getDiscountType,body.getDiscountType())
                .orderByDesc(CouponTemplateDO::getId));
    }
    /** 原App模板分页：只展示本店直接领取、启用且未到期模板，领完仍显示结果。 */
    default PageResult<CouponTemplateDO> selectAppPage(cn.qinghe.mall.framework.common.pojo.PageParam page,java.time.LocalDateTime now) {
        return selectPage(page,new LambdaQueryWrapperX<CouponTemplateDO>().eq(CouponTemplateDO::getTenantId,tenant())
                .eq(CouponTemplateDO::getStatus,0).eq(CouponTemplateDO::getTakeType,1)
                .and(q -> q.eq(CouponTemplateDO::getValidityType,2).or(w -> w.eq(CouponTemplateDO::getValidityType,1).gt(CouponTemplateDO::getValidEndTime,now)))
                .orderByDesc(CouponTemplateDO::getId));
    }
    @Update("UPDATE promotion_coupon_template SET take_count=take_count+#{count},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND status=0 AND #{count}>0 "
            + "AND take_count<=2000000000-#{count} AND (total_count=-1 OR take_count<=total_count-#{count})")
    int incrementTakeCount(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("count") Integer count);
    @Update("UPDATE promotion_coupon_template SET name=#{row.name},description=#{row.description},total_count=#{row.totalCount}, "
            + "take_limit_count=#{row.takeLimitCount},take_type=#{row.takeType},use_price=#{row.usePrice},product_scope=#{row.productScope}, "
            + "product_scope_values=CAST(#{scopeJson} AS JSON),validity_type=#{row.validityType},valid_start_time=#{row.validStartTime}, "
            + "valid_end_time=#{row.validEndTime},fixed_start_term=#{row.fixedStartTerm},fixed_end_term=#{row.fixedEndTerm}, "
            + "discount_type=#{row.discountType},discount_percent=#{row.discountPercent},discount_price=#{row.discountPrice}, "
            + "discount_limit_price=#{row.discountLimitPrice},auth_version=auth_version+1,updater=#{actor},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expected} AND auth_version<9223372036854775807 "
            + "AND (#{row.totalCount}=-1 OR take_count<=#{row.totalCount})")
    int updateRules(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,
            @Param("row") CouponTemplateDO row,@Param("scopeJson") String scopeJson,@Param("actor") String actor);
    @Update("UPDATE promotion_coupon_template SET status=#{status},auth_version=auth_version+1,updater=#{actor},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expected} AND auth_version<9223372036854775807")
    int updateStatus(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,@Param("status") Integer status,@Param("actor") String actor);
    @Update("UPDATE promotion_coupon_template SET deleted=b'1',auth_version=auth_version+1,updater=#{actor},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND status=1 AND take_count=0 AND auth_version=#{expected} AND auth_version<9223372036854775807")
    int retire(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,@Param("actor") String actor);
    @Insert("INSERT INTO promotion_coupon_template_audit(tenant_id,actor_id,template_id,action,from_version,to_version,details) "
            + "VALUES(#{tenantId},#{actorId},#{id},#{action},#{from},#{to},CAST(#{details} AS JSON))")
    int audit(@Param("tenantId") Long tenantId,@Param("actorId") Long actorId,@Param("id") Long id,@Param("action") String action,
            @Param("from") Long from,@Param("to") Long to,@Param("details") String details);
}
