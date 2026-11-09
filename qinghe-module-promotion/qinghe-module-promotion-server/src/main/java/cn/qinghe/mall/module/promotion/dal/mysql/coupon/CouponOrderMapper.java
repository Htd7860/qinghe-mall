package cn.qinghe.mall.module.promotion.dal.mysql.coupon;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.*;
import org.apache.ibatis.annotations.*;
/** 固定SQL保存订单阶段/原始分摊及审计；不提供任意DO更新。 */
@Mapper
public interface CouponOrderMapper {
    // Repeated registration must acquire an exclusive lock, avoiding INSERT IGNORE shared-to-write upgrades.
    @Insert("INSERT INTO promotion_coupon_order_gate(tenant_id,order_id,user_id,coupon_id,closed) "
        + "VALUES(#{tenant},#{order},#{user},#{coupon},b'0') ON DUPLICATE KEY UPDATE order_id=order_id")
    int ensureGate(@Param("tenant") Long tenant,@Param("order") Long order,@Param("user") Long user,@Param("coupon") Long coupon);
    @Select("SELECT tenant_id,order_id,user_id,coupon_id,closed,reason FROM promotion_coupon_order_gate "
        + "WHERE tenant_id=#{tenant} AND order_id=#{order} FOR UPDATE")
    CouponOrderGateDO lockGate(@Param("tenant") Long tenant,@Param("order") Long order);
    @Update("UPDATE promotion_coupon_order_gate SET closed=b'1',reason=#{reason},update_time=CURRENT_TIMESTAMP(6) "
        + "WHERE tenant_id=#{tenant} AND order_id=#{order} AND closed=b'0'")
    int closeGate(@Param("tenant") Long tenant,@Param("order") Long order,@Param("reason") String reason);

    @Select("SELECT id,tenant_id,user_id,order_id,coupon_id,request_digest,state,quote_json,coupon_version,auth_version,return_reason,create_time,update_time "
        + "FROM promotion_coupon_order WHERE tenant_id=#{tenant} AND order_id=#{order}")
    CouponOrderDO find(@Param("tenant") Long tenant,@Param("order") Long order);
    @Select("SELECT id,tenant_id,user_id,order_id,coupon_id,request_digest,state,quote_json,coupon_version,auth_version,return_reason,create_time,update_time "
        + "FROM promotion_coupon_order WHERE tenant_id=#{tenant} AND order_id=#{order} FOR UPDATE")
    CouponOrderDO lock(@Param("tenant") Long tenant,@Param("order") Long order);
    @Insert("INSERT INTO promotion_coupon_order(tenant_id,user_id,order_id,coupon_id,request_digest,state,quote_json,coupon_version,auth_version) "
        + "VALUES(#{row.tenantId},#{row.userId},#{row.orderId},#{row.couponId},#{row.requestDigest},1,CAST(#{row.quoteJson} AS JSON),#{row.couponVersion},0)")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insert(@Param("row") CouponOrderDO row);
    @Update("UPDATE promotion_coupon_order SET state=#{to},return_reason=#{reason},auth_version=auth_version+1,update_time=CURRENT_TIMESTAMP(6) "
        + "WHERE tenant_id=#{tenant} AND order_id=#{order} AND user_id=#{user} AND coupon_id=#{coupon} AND state=#{from} "
        + "AND auth_version=#{version} AND auth_version<9223372036854775807")
    int transition(@Param("tenant") Long tenant,@Param("order") Long order,@Param("user") Long user,@Param("coupon") Long coupon,
        @Param("from") Integer from,@Param("to") Integer to,@Param("reason") String reason,@Param("version") Long version);
    @Insert("INSERT INTO promotion_coupon_audit(tenant_id,coupon_id,user_id,order_id,action,from_status,to_status,from_version,to_version,actor_id,reason) "
        + "VALUES(#{row.tenantId},#{row.couponId},#{row.userId},#{row.orderId},#{row.action},#{row.fromStatus},#{row.toStatus},#{row.fromVersion},#{row.toVersion},#{row.actorId},#{row.reason})")
    int audit(@Param("row") CouponAuditDO row);
}
