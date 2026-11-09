package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 同一活动父锁内核对终局/缓存/余额，保存原代次基线；不是订单或资金结清。 */
@Mapper
public interface SeckillClearingMapper {
    /** 仅发现原关闭代次；实际清算仍在活动父锁内重新核对全部义务和余额。 */
    String CANDIDATE_FROM=" FROM promotion_seckill_activity a JOIN promotion_seckill_redis_epoch e ON e.tenant_id=a.tenant_id "
            + "AND e.activity_id=a.id AND e.generation=a.generation AND e.epoch=a.redis_epoch AND e.publish_request_id=a.publish_operation_no "
            + "WHERE a.status IN(30,40) AND a.redis_epoch=1 AND a.deleted=b'0' "
            + "AND ((e.status=30 AND e.stop_cache_state='SYNCED') OR (e.status=40 AND e.stop_cache_state='NOT_REQUIRED' AND e.init_token IS NULL AND e.init_deadline IS NULL)) "
            + "AND EXISTS(SELECT 1 FROM promotion_seckill_activity_sku p WHERE p.tenant_id=a.tenant_id AND p.activity_id=a.id) "
            + "AND NOT EXISTS(SELECT 1 FROM promotion_seckill_activity_sku p WHERE p.tenant_id=a.tenant_id AND p.activity_id=a.id "
            + "AND (p.allocation_status<>20 OR p.available_quantity<>0 OR p.held_quantity<>0 OR p.return_pending_quantity<>0))";

    @Select("SELECT a.*"+CANDIDATE_FROM+" AND a.tenant_id=#{tenant} AND (#{after} IS NULL OR a.id>#{after}) ORDER BY a.id LIMIT #{limit}")
    List<SeckillActivityDO> candidates(@Param("tenant") Long tenant,@Param("after") Long after,@Param("limit") int limit);

    @Select("SELECT * FROM promotion_seckill_activity_sku WHERE tenant_id=#{tenant} AND activity_id=#{activity} ORDER BY sku_id LIMIT 201 FOR UPDATE")
    List<SeckillActivitySkuDO> lockPools(@Param("tenant") Long tenant,@Param("activity") Long activity);

    @Select("SELECT sku_id,SUM(allocated_delta) AS allocated_quantity,SUM(available_delta) AS available_quantity,SUM(held_delta) AS held_quantity,"
            + "SUM(sold_delta) AS sold_quantity,SUM(returned_delta) AS returned_quantity FROM promotion_seckill_quota_entry "
            + "WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} GROUP BY sku_id ORDER BY sku_id LIMIT 201")
    List<LedgerBalance> ledger(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    String PENDING_ADMISSION="(EXISTS(SELECT 1 FROM promotion_seckill_admission_intent WHERE tenant_id=#{tenant} AND "
            + "activity_id=#{activity} AND generation=#{generation} AND (status=0 OR cache_sync_state='PENDING')))";
    String PENDING_REQUESTS="(EXISTS(SELECT 1 FROM promotion_seckill_request r WHERE r.tenant_id=#{tenant} AND r.activity_id=#{activity} "
            + "AND r.generation=#{generation} AND NOT ((r.order_id IS NULL AND r.status=50 AND EXISTS(SELECT 1 FROM "
            + "promotion_seckill_order_result x JOIN promotion_seckill_quota_entry e ON e.tenant_id=r.tenant_id AND "
            + "e.operation_no=CONCAT('trade-result:',r.request_no) AND e.request_no=r.request_no AND "
            + "e.activity_id=r.activity_id AND e.generation=r.generation AND e.sku_id=r.sku_id AND e.member_id=r.member_id "
            + "AND e.event_type='CREATE_CLOSED' AND e.source_event_id=x.event_id AND e.available_delta=r.quantity AND "
            + "e.held_delta=-r.quantity AND e.member_held_delta=-r.quantity AND e.sold_delta=0 AND e.returned_delta=0 AND "
            + "e.allocated_delta=0 AND e.member_paid_delta=0 WHERE x.tenant_id=r.tenant_id AND x.request_no=r.request_no AND "
            + "x.result_state=20 AND x.order_id IS NULL AND x.cache_sync_state='SYNCED' AND "
            + "r.fence_version>=x.aggregate_version AND r.version>=x.request_version)) OR (r.order_id IS NOT NULL AND "
            + "r.resource_cache_state='SYNCED' AND r.resource_order_version IS NOT NULL AND r.resource_pay_order_id IS NOT "
            + "NULL AND r.resource_time IS NOT NULL AND EXISTS(SELECT 1 FROM promotion_seckill_quota_entry e WHERE "
            + "e.tenant_id=r.tenant_id AND e.operation_no=CONCAT('order-resource:',r.request_no) AND "
            + "e.request_no=r.request_no AND e.activity_id=r.activity_id AND e.generation=r.generation AND e.sku_id=r.sku_id "
            + "AND e.member_id=r.member_id AND e.held_delta=-r.quantity AND e.member_held_delta=-r.quantity AND "
            + "e.allocated_delta=0 AND e.returned_delta=0 AND "
            + "e.source_event_id=CONCAT('order:',r.order_id,':',r.resource_order_version) AND ((r.resource_state='PAID' AND "
            + "e.event_type='ORDER_PAID' AND e.available_delta=0 AND e.sold_delta=r.quantity AND "
            + "e.member_paid_delta=r.quantity) OR (r.resource_state='CANCELLED' AND r.status=50 AND "
            + "e.event_type='ORDER_CANCELLED' AND e.available_delta=r.quantity AND e.sold_delta=0 AND "
            + "e.member_paid_delta=0)))))))";
    String PENDING_OUTBOX="(EXISTS(SELECT 1 FROM promotion_seckill_outbox o JOIN promotion_seckill_request r ON r.tenant_id=o.tenant_id "
            + "AND r.request_no=o.request_no WHERE r.tenant_id=#{tenant} AND r.activity_id=#{activity} AND "
            + "r.generation=#{generation} AND o.status<>'PUBLISHED'))";
    String PENDING_RESULTS="(EXISTS(SELECT 1 FROM promotion_seckill_order_result x JOIN promotion_seckill_request r ON "
            + "r.tenant_id=x.tenant_id AND r.request_no=x.request_no WHERE r.tenant_id=#{tenant} AND "
            + "r.activity_id=#{activity} AND r.generation=#{generation} AND x.cache_sync_state='PENDING'))";
    String PENDING_REFUNDS="(EXISTS(SELECT 1 FROM promotion_seckill_refund_record WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND generation=#{generation} AND (state='PENDING' OR (state='RESTORED' AND cache_state<>'SYNCED'))))";
    String PENDING_RETURNS="(EXISTS(SELECT 1 FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND "
            + "generation=#{generation} AND (state='PENDING' OR (state='CONFIRMED' AND cache_state<>'SYNCED'))))";
    String PENDING_QUOTA="(EXISTS(SELECT 1 FROM promotion_seckill_member_quota WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND generation=#{generation} AND held_quantity<>0))";

    @Select("SELECT "+PENDING_ADMISSION+"+"+PENDING_REQUESTS+"+"+PENDING_OUTBOX+"+"+PENDING_RESULTS+"+"+PENDING_REFUNDS+"+"+PENDING_RETURNS+"+"+PENDING_QUOTA)
    Long pendingObligations(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Select("SELECT COALESCE((SELECT SUM(paid_quantity) FROM promotion_seckill_member_quota WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND generation=#{generation}),0)=COALESCE((SELECT SUM(quantity) FROM promotion_seckill_request WHERE tenant_id=#{tenant} "
            + "AND activity_id=#{activity} AND generation=#{generation} AND resource_state='PAID'),0)")
    int paidQuotaMatches(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Insert("INSERT INTO promotion_seckill_clearing_pool(tenant_id,activity_id,generation,redis_epoch,sku_id,allocation_operation_no,allocation_snapshot,"
            + "allocated_quantity,sold_quantity,returned_quantity,product_version,product_status,definition_digest,pool_version,activity_version,created_at) "
            + "VALUES(#{r.tenantId},#{r.activityId},#{r.generation},#{r.redisEpoch},#{r.skuId},#{r.allocationOperationNo},CAST(#{r.allocationSnapshot} AS JSON),"
            + "#{r.allocatedQuantity},#{r.soldQuantity},#{r.returnedQuantity},#{r.productVersion},#{r.productStatus},#{r.definitionDigest},#{r.poolVersion},#{r.activityVersion},UTC_TIMESTAMP(3))")
    int insert(@Param("r") SeckillClearingPoolDO original);

    @Update("UPDATE promotion_seckill_activity SET status=40,version=version+1,updater='SYSTEM:CLEARING',update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} AND status=30 AND redis_epoch=1 AND publish_operation_no=#{request} "
            + "AND version=#{version} AND version<9007199254740991 AND deleted=b'0'")
    int begin(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("request") String request,@Param("version") Long version);

    @Select("SELECT p.* FROM promotion_seckill_clearing_pool p JOIN promotion_seckill_activity_audit a ON a.tenant_id=p.tenant_id AND a.activity_id=p.activity_id "
            + "AND a.action='CLEARING' AND a.actor_id=0 AND a.to_version=p.activity_version AND JSON_EXTRACT(a.details,'$.generation')=p.generation "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(a.details,'$.definitionDigest'))=p.definition_digest "
            + "WHERE p.tenant_id=#{tenant} AND p.activity_id=#{activity} AND p.generation=#{generation} ORDER BY p.sku_id LIMIT 201")
    List<SeckillClearingPoolDO> sealed(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Update("UPDATE promotion_seckill_activity SET status=50,version=version+1,updater='SYSTEM:CLEARING',update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} AND status=40 AND redis_epoch=1 AND publish_operation_no=#{request} "
            + "AND version=#{version} AND version<9007199254740991 AND deleted=b'0'")
    int complete(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("request") String request,@Param("version") Long version);

    @Select("SELECT COUNT(*) FROM promotion_seckill_activity_audit d WHERE d.tenant_id=#{tenant} AND d.activity_id=#{activity} "
            + "AND d.action='CLEARING_DONE' AND d.actor_id=0 AND JSON_EXTRACT(d.details,'$.generation')=#{generation} "
            + "AND EXISTS(SELECT 1 FROM promotion_seckill_clearing_pool c JOIN promotion_seckill_redis_epoch e ON e.tenant_id=c.tenant_id "
            + "AND e.activity_id=c.activity_id AND e.generation=c.generation AND e.epoch=c.redis_epoch AND e.definition_digest=c.definition_digest "
            + "WHERE c.tenant_id=d.tenant_id AND c.activity_id=d.activity_id AND c.generation=#{generation} AND d.from_version=c.activity_version "
            + "AND d.to_version=c.activity_version+1 AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.requestId'))=e.publish_request_id "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.definitionDigest'))=c.definition_digest)")
    Long completed(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    /** 只重置已封存原代次的当前投影；原sold/returned事实仍在不可变基线和流水内。 */
    @Update("UPDATE promotion_seckill_activity_sku p JOIN promotion_seckill_clearing_pool c ON c.tenant_id=p.tenant_id "
            + "AND c.activity_id=p.activity_id AND c.sku_id=p.sku_id AND c.generation=#{generation} "
            + "JOIN promotion_seckill_activity a ON a.tenant_id=p.tenant_id AND a.id=p.activity_id AND a.generation=c.generation "
            + "SET p.allocated_quantity=0,p.available_quantity=0,p.held_quantity=0,p.sold_quantity=0,p.returned_quantity=0,p.return_pending_quantity=0,"
            + "p.allocation_status=0,p.allocation_operation_no=NULL,p.version=p.version+1,p.update_time=UTC_TIMESTAMP(3) "
            + "WHERE p.tenant_id=#{tenant} AND p.activity_id=#{activity} AND a.status=50 AND a.redis_epoch=1 AND a.deleted=b'0' "
            + "AND a.publish_operation_no=#{request} AND p.allocation_status=20 AND p.allocation_operation_no=c.allocation_operation_no "
            + "AND p.allocated_quantity=c.allocated_quantity AND p.sold_quantity=c.sold_quantity AND p.returned_quantity=c.returned_quantity "
            + "AND p.available_quantity=0 AND p.held_quantity=0 AND p.return_pending_quantity=0 AND p.version=c.pool_version AND p.version<9007199254740991")
    int resetClearedSkus(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("request") String request);

    @Update("UPDATE promotion_seckill_activity SET status=0,publish_operation_no=NULL,redis_epoch=0,version=version+1,"
            + "updater=#{actor},update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} "
            + "AND publish_operation_no=#{request} AND status=50 AND redis_epoch=1 AND version=#{version} AND version<9007199254740991 AND deleted=b'0'")
    int reopenCleared(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("version") Long version,@Param("actor") String actor);

    @Data class LedgerBalance {private Long skuId;private Long allocatedQuantity;private Long availableQuantity;private Long heldQuantity;private Long soldQuantity;private Long returnedQuantity;}
}
