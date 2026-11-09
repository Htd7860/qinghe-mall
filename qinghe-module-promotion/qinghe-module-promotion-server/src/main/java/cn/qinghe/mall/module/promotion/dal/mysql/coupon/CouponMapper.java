package cn.qinghe.mall.module.promotion.dal.mysql.coupon;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.CouponDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
/** 原本人券查询保留；成功发放统计与结果查询包含逻辑删除记录，不能借删券返还额度。
 * @author 芋道源码
 */
@Mapper
public interface CouponMapper extends BaseMapperX<CouponDO> {
    default Long tenant() {
        Long id=TenantContextHolder.getRequiredTenantId();
        if(id<=0 || TenantContextHolder.isIgnore())throw new IllegalStateException("会员券查询需要可信租户");
        return id;
    }
    default CouponDO selectByIdAndUserId(Long id,Long userId) {
        return selectOne(new LambdaQueryWrapperX<CouponDO>().eq(CouponDO::getTenantId,tenant()).eq(CouponDO::getId,id).eq(CouponDO::getUserId,userId));
    }
    default PageResult<CouponDO> selectOwnedPage(Long userId,Integer status,PageParam page,LocalDateTime now) {
        LambdaQueryWrapperX<CouponDO> query=new LambdaQueryWrapperX<CouponDO>().eq(CouponDO::getTenantId,tenant()).eq(CouponDO::getUserId,userId);
        if(Integer.valueOf(3).equals(status))query.and(q -> q.eq(CouponDO::getStatus,3).or(w -> w.eq(CouponDO::getStatus,1).le(CouponDO::getValidEndTime,now)));
        else if(Integer.valueOf(1).equals(status))query.eq(CouponDO::getStatus,1).gt(CouponDO::getValidEndTime,now);
        else query.eqIfPresent(CouponDO::getStatus,status);
        return selectPage(page,query.orderByDesc(CouponDO::getId));
    }
    default CouponDO selectOwnedForUpdate(Long id,Long userId) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        return selectOne(new LambdaQueryWrapperX<CouponDO>().eq(CouponDO::getTenantId,tenant()).eq(CouponDO::getUserId,userId)
                .eq(CouponDO::getId,id).last("FOR UPDATE"));
    }
    @Update("UPDATE promotion_coupon SET status=4,locked_order_id=#{order},locked_time=#{now},auth_version=auth_version+1,updater='trade-order',update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND deleted=b'0' AND status=1 AND invalidated_time IS NULL "
        + "AND locked_order_id IS NULL AND use_order_id IS NULL AND auth_version=#{version} AND auth_version<9223372036854775807 "
        + "AND valid_start_time<=#{now} AND valid_end_time>#{now}")
    int holdForOrder(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("order") Long order,
        @Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE promotion_coupon SET status=2,use_order_id=#{order},use_time=#{now},locked_order_id=NULL,locked_time=NULL,auth_version=auth_version+1,updater='trade-order',update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND deleted=b'0' AND status=4 AND locked_order_id=#{order} "
        + "AND use_order_id IS NULL AND invalidated_time IS NULL AND auth_version=#{version} AND auth_version<9223372036854775807 "
        + "AND valid_start_time<=#{now} AND valid_end_time>#{now}")
    int useForOrder(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("order") Long order,
        @Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE promotion_coupon SET status=CASE WHEN invalidated_time IS NOT NULL OR status=5 THEN 5 WHEN valid_end_time<=#{now} THEN 3 ELSE 1 END, "
        + "use_order_id=NULL,use_time=NULL,locked_order_id=NULL,locked_time=NULL,auth_version=auth_version+1,updater='trade-order',update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND deleted=b'0' AND auth_version=#{version} AND auth_version<9223372036854775807 "
        + "AND ((#{phase}=1 AND status IN(4,5) AND locked_order_id=#{order} AND use_order_id IS NULL) "
        + "OR (#{phase}=2 AND status IN(2,5) AND use_order_id=#{order} AND locked_order_id IS NULL))")
    int restoreForOrder(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("order") Long order,
        @Param("phase") Integer phase,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE promotion_coupon SET status=5,invalidated_time=#{now},auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND deleted=b'0' AND status IN(1,2,3,4) AND invalidated_time IS NULL "
        + "AND auth_version=#{version} AND auth_version<9223372036854775807")
    int invalidate(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("version") Long version,
        @Param("actor") String actor,@Param("now") LocalDateTime now);
    @lombok.Data
    class TakeCount { private Long templateId;private Long takenCount; }
    /** 一次读取当前页个人历史成功量，含已逻辑删除的成功记录。 */
    @Select({"<script>SELECT template_id,COUNT(*) AS taken_count FROM promotion_coupon WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND template_id IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY template_id</script>"})
    java.util.List<TakeCount> selectTakeCounts(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("ids") java.util.List<Long> ids);
    @Select("SELECT COUNT(*) FROM promotion_coupon WHERE tenant_id=#{tenantId} AND template_id=#{templateId} AND user_id=#{userId}")
    Long countSuccessfulGrants(@Param("tenantId") Long tenantId,@Param("templateId") Long templateId,@Param("userId") Long userId);
    /** 仅恢复成功发放回执，不读取/反序列化JSON，也不把回执当当前可用性。 */
    @Select("SELECT id,tenant_id,template_id,user_id,take_type,grant_key FROM promotion_coupon "
            + "WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND grant_key=#{grantKey}")
    CouponDO selectGrantReceipt(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("grantKey") String grantKey);
}
