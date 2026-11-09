package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 活动父锁内的原申请资源转换；条件更新和流水一起提交，不直接同步Redis。 */
@Mapper
public interface SeckillOrderResourceMapper {
    /** Joined durable terminal + actual journal, not a caller-supplied paid/cancelled DTO. */
    String PROOF=" FROM promotion_seckill_request r JOIN promotion_seckill_quota_entry e ON e.tenant_id=r.tenant_id "
            + "AND e.operation_no=CONCAT('order-resource:',r.request_no) AND e.request_no=r.request_no "
            + "AND e.activity_id=r.activity_id AND e.generation=r.generation AND e.sku_id=r.sku_id AND e.member_id=r.member_id "
            + "AND e.source_event_id=CONCAT('order:',r.order_id,':',r.resource_order_version) "
            + "AND e.held_delta=-r.quantity AND e.member_held_delta=-r.quantity AND e.allocated_delta=0 AND e.returned_delta=0 "
            + "WHERE r.tenant_id=#{tenant} AND r.request_no=#{request} AND r.order_id IS NOT NULL "
            + "AND r.resource_order_version IS NOT NULL AND r.resource_pay_order_id IS NOT NULL AND r.resource_time IS NOT NULL "
            + "AND r.resource_cache_state IN('PENDING','SYNCED') AND ((r.resource_state='PAID' AND e.event_type='ORDER_PAID' "
            + "AND e.available_delta=0 AND e.sold_delta=r.quantity AND e.member_paid_delta=r.quantity) "
            + "OR (r.resource_state='CANCELLED' AND r.status=50 AND e.event_type='ORDER_CANCELLED' "
            + "AND e.available_delta=r.quantity AND e.sold_delta=0 AND e.member_paid_delta=0))";
    @Select("SELECT r.*"+PROOF)
    SeckillRequestDO cacheProof(@Param("tenant") Long tenant,@Param("request") String request);

    @Update("UPDATE promotion_seckill_request SET resource_cache_state='SYNCED',update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND resource_state=#{r.resourceState} "
            + "AND resource_order_version=#{r.resourceOrderVersion} AND resource_pay_order_id=#{r.resourcePayOrderId} "
            + "AND order_id=#{r.orderId} AND resource_time=#{r.resourceTime} AND resource_cache_state='PENDING'")
    int cacheSynced(@Param("r") SeckillRequestDO original);

    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND resource_cache_state='PENDING' "
            + "AND resource_state IN('PAID','CANCELLED') AND (#{after} IS NULL OR request_no>#{after}) ORDER BY request_no LIMIT #{limit}")
    java.util.List<SeckillRequestDO> pendingCache(@Param("tenant") Long tenant,@Param("after") String after,@Param("limit") int limit);

    @Update("UPDATE promotion_seckill_activity_sku SET held_quantity=held_quantity-#{r.quantity},"
            + "sold_quantity=sold_quantity+CASE WHEN #{paid} THEN #{r.quantity} ELSE 0 END,"
            + "available_quantity=available_quantity+CASE WHEN #{paid} THEN 0 ELSE #{r.quantity} END,"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND activity_id=#{r.activityId} "
            + "AND sku_id=#{r.skuId} AND version=#{version} AND version<9007199254740991 AND held_quantity>=#{r.quantity} AND #{r.quantity}>0")
    int pool(@Param("r") SeckillRequestDO r,@Param("version") Long version,@Param("paid") boolean paid);

    @Update("UPDATE promotion_seckill_member_quota SET held_quantity=held_quantity-#{r.quantity},"
            + "paid_quantity=paid_quantity+CASE WHEN #{paid} THEN #{r.quantity} ELSE 0 END,version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND activity_id=#{r.activityId} AND generation=#{r.generation} AND member_id=#{r.memberId} "
            + "AND version=#{version} AND version<9007199254740991 AND held_quantity>=#{r.quantity} AND #{r.quantity}>0")
    int member(@Param("r") SeckillRequestDO r,@Param("version") Long version,@Param("paid") boolean paid);

    @Update("UPDATE promotion_seckill_request SET resource_state=#{state},resource_order_version=#{orderVersion},resource_pay_order_id=#{pay},"
            + "resource_time=#{time},resource_cache_state='PENDING',status=CASE WHEN #{state}='CANCELLED' THEN 50 ELSE status END,"
            + "claim_token=NULL,locked_until=NULL,version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND version=#{r.version} AND version<9007199254740991 "
            + "AND order_id=#{r.orderId} AND order_id IS NOT NULL AND status IN(20,30,60) AND resource_state='HELD'")
    int complete(@Param("r") SeckillRequestDO r,@Param("state") String state,@Param("orderVersion") Long orderVersion,
            @Param("pay") Long payOrder,@Param("time") LocalDateTime time);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,member_id,request_no,event_type,"
            + "available_delta,held_delta,sold_delta,member_held_delta,member_paid_delta,source_event_id,create_time) "
            + "VALUES(#{r.tenantId},CONCAT('order-resource:',#{r.requestNo}),#{r.activityId},#{r.generation},#{r.skuId},#{r.memberId},#{r.requestNo},"
            + "#{type},CASE WHEN #{paid} THEN 0 ELSE #{r.quantity} END,-#{r.quantity},CASE WHEN #{paid} THEN #{r.quantity} ELSE 0 END,"
            + "-#{r.quantity},CASE WHEN #{paid} THEN #{r.quantity} ELSE 0 END,CONCAT('order:',#{r.orderId},':',#{orderVersion}),UTC_TIMESTAMP(3))")
    int journal(@Param("r") SeckillRequestDO r,@Param("type") String type,@Param("paid") boolean paid,@Param("orderVersion") Long orderVersion);

    @Select("SELECT COUNT(*) FROM promotion_seckill_quota_entry WHERE tenant_id=#{r.tenantId} AND operation_no=CONCAT('order-resource:',#{r.requestNo}) "
            + "AND activity_id=#{r.activityId} AND generation=#{r.generation} AND sku_id=#{r.skuId} AND member_id=#{r.memberId} AND request_no=#{r.requestNo} "
            + "AND event_type=#{type} AND held_delta=-#{r.quantity} AND member_held_delta=-#{r.quantity} AND allocated_delta=0 AND returned_delta=0 "
            + "AND source_event_id=CONCAT('order:',#{r.orderId},':',#{r.resourceOrderVersion}) "
            + "AND available_delta=CASE WHEN #{paid} THEN 0 ELSE #{r.quantity} END AND sold_delta=CASE WHEN #{paid} THEN #{r.quantity} ELSE 0 END "
            + "AND member_paid_delta=CASE WHEN #{paid} THEN #{r.quantity} ELSE 0 END")
    long journalExists(@Param("r") SeckillRequestDO r,@Param("type") String type,@Param("paid") boolean paid);
}
