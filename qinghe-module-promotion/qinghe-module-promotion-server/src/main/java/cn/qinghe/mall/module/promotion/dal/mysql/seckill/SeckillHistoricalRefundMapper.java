package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import lombok.Data;
import org.apache.ibatis.annotations.*;

/** 旧代次售后复用原累计归还计划；归还普通库存，不增减当前活动池。 */
@Mapper
public interface SeckillHistoricalRefundMapper {
    @Select("SELECT c.* FROM promotion_seckill_clearing_pool c JOIN promotion_seckill_activity_audit a ON a.tenant_id=c.tenant_id "
            + "AND a.activity_id=c.activity_id AND a.action='CLEARING' AND a.actor_id=0 AND a.to_version=c.activity_version "
            + "AND JSON_EXTRACT(a.details,'$.generation')=c.generation AND JSON_UNQUOTE(JSON_EXTRACT(a.details,'$.definitionDigest'))=c.definition_digest "
            + "WHERE c.tenant_id=#{tenant} AND c.activity_id=#{activity} AND c.generation=#{generation} AND c.sku_id=#{sku}")
    SeckillClearingPoolDO baseline(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("sku") Long sku);

    /** 恢复草稿但尚未新prepare时，generation仍是旧值；只能凭真实完成/重编审计继续历史退款。 */
    @Select("SELECT COUNT(*) FROM promotion_seckill_activity_audit r WHERE r.tenant_id=#{tenant} AND r.activity_id=#{activity} "
            + "AND r.action='REOPEN' AND r.actor_id>0 AND EXISTS(SELECT 1 FROM promotion_seckill_clearing_pool c "
            + "JOIN promotion_seckill_redis_epoch e ON e.tenant_id=c.tenant_id AND e.activity_id=c.activity_id AND e.generation=c.generation "
            + "AND e.epoch=c.redis_epoch AND e.definition_digest=c.definition_digest "
            + "JOIN promotion_seckill_activity_audit d ON d.tenant_id=c.tenant_id AND d.activity_id=c.activity_id AND d.action='CLEARING_DONE' "
            + "AND d.actor_id=0 AND d.from_version=c.activity_version AND d.to_version=c.activity_version+1 "
            + "AND JSON_EXTRACT(d.details,'$.generation')=c.generation AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.requestId'))=e.publish_request_id "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.definitionDigest'))=c.definition_digest "
            + "WHERE c.tenant_id=r.tenant_id AND c.activity_id=r.activity_id AND c.generation=#{generation} "
            + "AND r.from_version=d.to_version AND r.to_version=d.to_version+1 "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(r.details,'$.requestId'))=e.publish_request_id)")
    Long reopened(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Select("SELECT * FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND after_sale_id=#{sale} AND source_kind='HISTORICAL_REFUND'")
    SeckillPoolReturnDO plan(@Param("tenant") Long tenant,@Param("sale") Long sale);

    String CONFIRMED_FROM=" FROM promotion_seckill_pool_return p "
            + "JOIN promotion_seckill_refund_record f ON f.tenant_id=p.tenant_id AND f.after_sale_id=p.after_sale_id AND f.activity_id=p.activity_id "
            + "AND f.generation=p.generation AND f.sku_id=p.sku_id AND f.state='RESTORED' AND f.restore_count=p.quantity AND f.cache_state='SYNCED' "
            + "JOIN promotion_seckill_stock_receipt s ON s.tenant_id=p.tenant_id AND s.activity_id=p.activity_id AND s.generation=p.generation "
            + "AND s.sku_id=p.sku_id AND s.product_version=p.product_version AND s.product_status=p.product_status AND s.returned_quantity=p.target_returned "
            + "JOIN promotion_seckill_quota_entry e ON e.tenant_id=f.tenant_id AND e.operation_no=CONCAT('refund-stock:',f.after_sale_id) "
            + "AND e.activity_id=f.activity_id AND e.generation=f.generation AND e.sku_id=f.sku_id AND e.member_id=f.member_id AND e.request_no=f.request_no "
            + "AND e.event_type='ORDER_REFUND_RETURNED' AND e.sold_delta=-f.restore_count AND e.returned_delta=f.restore_count "
            + "AND e.available_delta=0 AND e.held_delta=0 AND e.allocated_delta=0 AND e.member_held_delta=0 AND e.member_paid_delta=0 "
            + "AND e.source_event_id=CONCAT('refund:',f.after_sale_id,':',f.order_version) "
            + "WHERE p.tenant_id=#{tenant} AND p.activity_id=#{activity} AND p.generation=#{generation} "
            + "AND p.source_kind='HISTORICAL_REFUND' AND p.state='CONFIRMED' AND p.cache_state='SYNCED'";
    @Select("SELECT COALESCE(SUM(p.quantity),0) AS returned_count,COUNT(*) AS confirmed_plans"+CONFIRMED_FROM+" AND p.sku_id=#{sku}")
    Progress progress(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("sku") Long sku);

    @Select("SELECT p.sku_id,SUM(p.quantity) AS returned_count,COUNT(*) AS confirmed_plans"+CONFIRMED_FROM+" GROUP BY p.sku_id ORDER BY p.sku_id LIMIT 201")
    java.util.List<Progress> progressAll(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Insert("INSERT INTO promotion_seckill_pool_return(tenant_id,return_no,activity_id,generation,redis_epoch,sku_id,allocation_operation_no,allocation_snapshot,"
            + "quantity,base_returned,target_returned,base_product_version,state,cache_state,version,create_time,update_time,source_kind,after_sale_id) "
            + "VALUES(#{p.tenantId},#{p.returnNo},#{p.activityId},#{p.generation},#{p.redisEpoch},#{p.skuId},#{p.allocationOperationNo},CAST(#{p.allocationSnapshot} AS JSON),"
            + "#{p.quantity},#{p.baseReturned},#{p.targetReturned},#{p.baseProductVersion},'PENDING','NOT_REQUIRED',0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'HISTORICAL_REFUND',#{p.afterSaleId})")
    int insert(@Param("p") SeckillPoolReturnDO plan);

    @Update("UPDATE promotion_seckill_pool_return SET state='CONFIRMED',product_version=#{productVersion},product_status=#{productStatus},"
            + "confirmed_time=UTC_TIMESTAMP(3),cache_state='SYNCED',version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{p.tenantId} AND return_no=#{p.returnNo} AND after_sale_id=#{p.afterSaleId} AND source_kind='HISTORICAL_REFUND' "
            + "AND state='PENDING' AND version=#{p.version} AND version<9007199254740991 AND target_returned=#{p.targetReturned} AND base_product_version=#{p.baseProductVersion}")
    int confirmed(@Param("p") SeckillPoolReturnDO original,@Param("productVersion") Long productVersion,@Param("productStatus") Integer productStatus);

    @Update("UPDATE promotion_seckill_refund_record SET state='RESTORED',restored_total=#{total},restored_time=UTC_TIMESTAMP(3),cache_state='SYNCED',"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{f.tenantId} AND after_sale_id=#{f.afterSaleId} AND request_no=#{f.requestNo} "
            + "AND state='PENDING' AND version=#{f.version} AND version<9007199254740991 AND restore_count=#{f.restoreCount} AND restore_count>0")
    int restored(@Param("f") SeckillRefundRecordDO refund,@Param("total") Integer total);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,member_id,request_no,event_type,"
            + "sold_delta,returned_delta,source_event_id,create_time) VALUES(#{f.tenantId},CONCAT('refund-stock:',#{f.afterSaleId}),#{f.activityId},"
            + "#{f.generation},#{f.skuId},#{f.memberId},#{f.requestNo},'ORDER_REFUND_RETURNED',-#{f.restoreCount},#{f.restoreCount},"
            + "CONCAT('refund:',#{f.afterSaleId},':',#{f.orderVersion}),UTC_TIMESTAMP(3))")
    int journal(@Param("f") SeckillRefundRecordDO refund);

    String RESTORED_PROOF=" FROM promotion_seckill_refund_record f JOIN promotion_seckill_pool_return p ON p.tenant_id=f.tenant_id "
            + "AND p.after_sale_id=f.after_sale_id AND p.source_kind='HISTORICAL_REFUND' AND p.activity_id=f.activity_id AND p.generation=f.generation "
            + "AND p.redis_epoch=f.redis_epoch AND p.sku_id=f.sku_id AND p.quantity=f.restore_count AND p.state='CONFIRMED' AND p.cache_state='SYNCED' "
            + "AND p.product_version=p.base_product_version+1 AND p.confirmed_time IS NOT NULL "
            + "JOIN promotion_seckill_clearing_pool c ON c.tenant_id=p.tenant_id AND c.activity_id=p.activity_id AND c.generation=p.generation "
            + "AND c.redis_epoch=p.redis_epoch AND c.sku_id=p.sku_id AND c.allocation_operation_no=p.allocation_operation_no "
            + "AND c.allocation_snapshot=p.allocation_snapshot AND p.base_returned>=c.returned_quantity AND p.target_returned<=c.allocated_quantity "
            + "JOIN promotion_seckill_activity_audit a ON a.tenant_id=c.tenant_id AND a.activity_id=c.activity_id AND a.action='CLEARING' AND a.actor_id=0 "
            + "AND a.to_version=c.activity_version AND JSON_EXTRACT(a.details,'$.generation')=c.generation "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(a.details,'$.definitionDigest'))=c.definition_digest "
            + "JOIN promotion_seckill_stock_receipt s ON s.tenant_id=p.tenant_id AND s.activity_id=p.activity_id AND s.generation=p.generation "
            + "AND s.sku_id=p.sku_id AND s.product_version=p.product_version AND s.product_status=p.product_status "
            + "AND s.returned_quantity=p.target_returned AND s.allocated_quantity=c.allocated_quantity "
            + "JOIN promotion_seckill_request r ON r.tenant_id=f.tenant_id AND r.request_no=f.request_no AND r.activity_id=f.activity_id "
            + "AND r.generation=f.generation AND r.redis_epoch=f.redis_epoch AND r.sku_id=f.sku_id AND r.order_id=f.order_id AND r.member_id=f.member_id "
            + "AND r.resource_state='PAID' AND f.restored_total<=r.quantity "
            + "JOIN promotion_seckill_quota_entry paid ON paid.tenant_id=r.tenant_id AND paid.operation_no=CONCAT('order-resource:',r.request_no) "
            + "AND paid.request_no=r.request_no AND paid.activity_id=r.activity_id AND paid.generation=r.generation AND paid.sku_id=r.sku_id "
            + "AND paid.member_id=r.member_id AND paid.event_type='ORDER_PAID' AND paid.source_event_id=CONCAT('order:',r.order_id,':',r.resource_order_version) "
            + "AND paid.held_delta=-r.quantity AND paid.member_held_delta=-r.quantity AND paid.available_delta=0 AND paid.sold_delta=r.quantity "
            + "AND paid.member_paid_delta=r.quantity AND paid.allocated_delta=0 AND paid.returned_delta=0 "
            + "JOIN promotion_seckill_quota_entry e ON e.tenant_id=f.tenant_id AND e.operation_no=CONCAT('refund-stock:',f.after_sale_id) "
            + "AND e.activity_id=f.activity_id AND e.generation=f.generation AND e.sku_id=f.sku_id AND e.member_id=f.member_id AND e.request_no=f.request_no "
            + "AND e.event_type='ORDER_REFUND_RETURNED' AND e.sold_delta=-f.restore_count AND e.returned_delta=f.restore_count "
            + "AND e.available_delta=0 AND e.held_delta=0 AND e.allocated_delta=0 AND e.member_held_delta=0 AND e.member_paid_delta=0 "
            + "AND e.source_event_id=CONCAT('refund:',f.after_sale_id,':',f.order_version) "
            + "WHERE f.tenant_id=#{tenant} AND f.after_sale_id=#{sale} AND f.state='RESTORED' AND f.cache_state='SYNCED' "
            + "AND f.restore_count>0 AND f.restored_total>=f.restore_count AND f.restored_time IS NOT NULL";
    @Select("SELECT f.*"+RESTORED_PROOF)
    SeckillRefundRecordDO restoredProof(@Param("tenant") Long tenant,@Param("sale") Long sale);

    @Data class Progress {private Long skuId;private Long returnedCount;private Long confirmedPlans;}
}
