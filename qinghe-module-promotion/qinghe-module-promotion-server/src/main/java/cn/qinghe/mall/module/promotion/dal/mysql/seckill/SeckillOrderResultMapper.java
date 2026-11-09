package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import org.apache.ibatis.annotations.*;

/** 活动→规格→额度→原申请，同事务推进、退回占用、追加流水和最终结果回执。 */
@Mapper
public interface SeckillOrderResultMapper {
    /** Keyset advances over failures as well; the scheduled caller can wrap after the final page. */
    @Select("SELECT * FROM promotion_seckill_order_result WHERE tenant_id=#{tenant} AND result_state=20 AND cache_sync_state='PENDING' "
            + "AND (#{afterTime} IS NULL OR processed_at>#{afterTime} OR (processed_at=#{afterTime} AND event_id>#{afterEvent})) "
            + "ORDER BY processed_at,event_id LIMIT #{limit}")
    java.util.List<SeckillOrderResultDO> pendingCache(@Param("tenant") Long tenant,@Param("afterTime") java.time.LocalDateTime afterTime,
            @Param("afterEvent") String afterEvent,@Param("limit") Integer limit);

    /** Only a verified permanent pre-order close plus its actual release journal authorizes Redis release. */
    String CLOSED_PROOF = " FROM promotion_seckill_order_result x JOIN promotion_seckill_request r "
            + "ON r.tenant_id=x.tenant_id AND r.request_no=x.request_no JOIN promotion_seckill_quota_entry e "
            + "ON e.tenant_id=r.tenant_id AND e.operation_no=CONCAT('trade-result:',r.request_no) "
            + "AND e.source_event_id=x.event_id AND e.request_no=r.request_no AND e.activity_id=r.activity_id "
            + "AND e.generation=r.generation AND e.sku_id=r.sku_id AND e.member_id=r.member_id "
            + "AND e.event_type='CREATE_CLOSED' AND e.available_delta=r.quantity AND e.held_delta=-r.quantity "
            + "AND e.member_held_delta=-r.quantity AND e.sold_delta=0 AND e.returned_delta=0 AND e.member_paid_delta=0 AND e.allocated_delta=0 "
            + "WHERE x.tenant_id=#{tenant} AND x.event_id=#{event} AND x.event_type='SECKILL_REQUEST_CLOSED' "
            + "AND x.result_state=20 AND x.order_id IS NULL AND x.aggregate_version>0 AND x.cache_sync_state IN('PENDING','SYNCED') "
            + "AND r.status=50 AND r.order_id IS NULL AND r.fence_version>=x.aggregate_version AND r.version>=x.request_version";

    @Select("SELECT r.*"+CLOSED_PROOF)
    SeckillRequestDO closedCacheProof(@Param("tenant") Long tenant,@Param("event") String event);

    @Update("UPDATE promotion_seckill_order_result x SET cache_sync_state='SYNCED',cache_synced_at=UTC_TIMESTAMP(3) "
            + "WHERE x.tenant_id=#{tenant} AND x.event_id=#{event} AND x.event_type='SECKILL_REQUEST_CLOSED' "
            + "AND x.result_state=20 AND x.order_id IS NULL AND x.cache_sync_state='PENDING' "
            + "AND EXISTS(SELECT 1 FROM promotion_seckill_request r WHERE r.tenant_id=x.tenant_id AND r.request_no=x.request_no "
            + "AND r.status=50 AND r.order_id IS NULL AND r.fence_version>=x.aggregate_version AND r.version>=x.request_version)")
    int cacheSynced(@Param("tenant") Long tenant,@Param("event") String event);

    @Select("SELECT * FROM promotion_seckill_order_result WHERE tenant_id=#{tenant} AND event_id=#{event}")
    SeckillOrderResultDO receipt(@Param("tenant") Long tenant,@Param("event") String event);

    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND request_no=#{request} FOR UPDATE")
    SeckillRequestDO lockRequest(@Param("tenant") Long tenant,@Param("request") String request);

    @Update("UPDATE promotion_seckill_request SET order_id=#{order},status=CASE WHEN status=10 THEN 20 ELSE status END,"
            + "fence_version=GREATEST(fence_version,#{fence}),version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND version=#{r.version} AND version<9007199254740991 "
            + "AND status IN(10,30,60) AND order_id IS NULL")
    int bindOrder(@Param("r") SeckillRequestDO request,@Param("order") Long order,@Param("fence") Long fence);

    @Update("UPDATE promotion_seckill_activity_sku SET available_quantity=available_quantity+#{r.quantity},held_quantity=held_quantity-#{r.quantity},"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND activity_id=#{r.activityId} "
            + "AND sku_id=#{r.skuId} AND version=#{version} AND version<9007199254740991 AND held_quantity>=#{r.quantity} AND #{r.quantity}>0")
    int releasePool(@Param("r") SeckillRequestDO request,@Param("version") Long version);

    @Update("UPDATE promotion_seckill_member_quota SET held_quantity=held_quantity-#{r.quantity},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND activity_id=#{r.activityId} AND generation=#{r.generation} AND member_id=#{r.memberId} "
            + "AND version=#{version} AND version<9007199254740991 AND held_quantity>=#{r.quantity} AND #{r.quantity}>0")
    int releaseMember(@Param("r") SeckillRequestDO request,@Param("version") Long version);

    @Update("UPDATE promotion_seckill_request SET status=50,fence_version=GREATEST(fence_version,#{fence}),failure_code=COALESCE(failure_code,409),"
            + "claim_token=NULL,locked_until=NULL,version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND version=#{r.version} AND version<9007199254740991 "
            + "AND status IN(10,30,40,60) AND order_id IS NULL")
    int closeBeforeOrder(@Param("r") SeckillRequestDO request,@Param("fence") Long fence);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,member_id,request_no,event_type,"
            + "available_delta,held_delta,member_held_delta,source_event_id,create_time) VALUES(#{r.tenantId},CONCAT('trade-result:',#{r.requestNo}),"
            + "#{r.activityId},#{r.generation},#{r.skuId},#{r.memberId},#{r.requestNo},#{type},#{delta},-#{delta},-#{delta},#{event},UTC_TIMESTAMP(3))")
    int journal(@Param("r") SeckillRequestDO request,@Param("type") String type,@Param("delta") Integer releasedQuantity,@Param("event") String event);

    @Insert("INSERT INTO promotion_seckill_order_result(tenant_id,event_id,request_no,aggregate_version,event_type,payload_hash,result_state,order_id,"
            + "request_version,cache_sync_state,processed_at) VALUES(#{r.tenantId},#{r.eventId},#{r.requestNo},#{r.aggregateVersion},#{r.eventType},"
            + "#{r.payloadHash},#{r.resultState},#{r.orderId},#{r.requestVersion},#{r.cacheSyncState},UTC_TIMESTAMP(3))")
    int complete(@Param("r") SeckillOrderResultDO receipt);
}
