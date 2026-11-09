package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRefundRecordDO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import org.apache.ibatis.annotations.*;

/** 原申请锁串行化退款登记/累计恢复；原池、退款状态及流水同事务，个人累计已购不改变。 */
@Mapper
public interface SeckillRefundRecordMapper {
    @Select("SELECT * FROM promotion_seckill_refund_record WHERE tenant_id=#{tenant} AND after_sale_id=#{sale}")
    SeckillRefundRecordDO get(@Param("tenant") Long tenant,@Param("sale") Long afterSale);

    @Select("SELECT * FROM promotion_seckill_refund_record WHERE tenant_id=#{tenant} AND after_sale_id=#{sale} FOR UPDATE")
    SeckillRefundRecordDO lock(@Param("tenant") Long tenant,@Param("sale") Long afterSale);

    @Select("SELECT COALESCE(SUM(restore_count),0) FROM promotion_seckill_refund_record "
            + "WHERE tenant_id=#{tenant} AND request_no=#{request} AND state='RESTORED'")
    Long restoredTotal(@Param("tenant") Long tenant,@Param("request") String request);

    @Update("UPDATE promotion_seckill_activity_sku SET sold_quantity=sold_quantity-#{f.restoreCount},"
            + "available_quantity=available_quantity+#{f.restoreCount},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{f.tenantId} AND activity_id=#{f.activityId} AND sku_id=#{f.skuId} AND allocation_status=20 "
            + "AND version=#{version} AND version<9007199254740991 AND #{f.restoreCount}>0 AND sold_quantity>=#{f.restoreCount}")
    int restorePool(@Param("f") SeckillRefundRecordDO refund,@Param("version") Long poolVersion);

    @Update("UPDATE promotion_seckill_refund_record SET state='RESTORED',restored_total=#{total},restored_time=UTC_TIMESTAMP(3),"
            + "cache_state='PENDING',version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{f.tenantId} AND after_sale_id=#{f.afterSaleId} "
            + "AND request_no=#{f.requestNo} AND version=#{f.version} AND version<9007199254740991 AND state='PENDING' AND restore_count>0")
    int restored(@Param("f") SeckillRefundRecordDO refund,@Param("total") Integer total);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,member_id,request_no,event_type,"
            + "available_delta,sold_delta,source_event_id,create_time) VALUES(#{f.tenantId},CONCAT('refund-stock:',#{f.afterSaleId}),"
            + "#{f.activityId},#{f.generation},#{f.skuId},#{f.memberId},#{f.requestNo},'ORDER_REFUND_RESTORED',#{f.restoreCount},-#{f.restoreCount},"
            + "CONCAT('refund:',#{f.afterSaleId},':',#{f.orderVersion}),UTC_TIMESTAMP(3))")
    int journal(@Param("f") SeckillRefundRecordDO refund);

    String JOURNAL=" FROM promotion_seckill_refund_record f JOIN promotion_seckill_quota_entry e "
            + "ON e.tenant_id=f.tenant_id AND e.operation_no=CONCAT('refund-stock:',f.after_sale_id) "
            + "AND e.activity_id=f.activity_id AND e.generation=f.generation AND e.sku_id=f.sku_id AND e.member_id=f.member_id "
            + "AND e.request_no=f.request_no AND e.event_type='ORDER_REFUND_RESTORED' "
            + "AND e.source_event_id=CONCAT('refund:',f.after_sale_id,':',f.order_version) AND e.available_delta=f.restore_count "
            + "AND e.sold_delta=-f.restore_count AND e.held_delta=0 AND e.allocated_delta=0 AND e.returned_delta=0 "
            + "AND e.member_held_delta=0 AND e.member_paid_delta=0 WHERE f.tenant_id=#{tenant} AND f.after_sale_id=#{sale} "
            + "AND f.state='RESTORED' AND f.restore_count>0 AND f.restored_total>=f.restore_count AND f.restored_time IS NOT NULL";
    @Select("SELECT f.*"+JOURNAL+" UNION ALL SELECT f.*"+SeckillHistoricalRefundMapper.RESTORED_PROOF)
    SeckillRefundRecordDO restoredProof(@Param("tenant") Long tenant,@Param("sale") Long afterSale);

    @Select("SELECT COUNT(*) FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND after_sale_id=#{sale} AND source_kind='HISTORICAL_REFUND'")
    int historicalReturn(@Param("tenant") Long tenant,@Param("sale") Long sale);

    @Update("UPDATE promotion_seckill_refund_record SET cache_state='SYNCED',update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{f.tenantId} AND after_sale_id=#{f.afterSaleId} AND request_no=#{f.requestNo} AND version=#{f.version} "
            + "AND state='RESTORED' AND restored_total=#{f.restoredTotal} AND restored_time=#{f.restoredTime} AND cache_state='PENDING'")
    int cacheSynced(@Param("f") SeckillRefundRecordDO proof);

    @Select("SELECT CASE WHEN COALESCE(SUM(refunded_count),0)+#{f.refundedCount}<=#{r.quantity} "
            + "AND COALESCE(SUM(restore_count),0)+#{f.restoreCount}<=#{r.quantity} "
            + "AND COALESCE(SUM(refund_amount),0)+#{f.refundAmount}<=#{r.payAmount} THEN 1 ELSE 0 END "
            + "FROM promotion_seckill_refund_record WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo}")
    int withinOriginal(@Param("r") SeckillRequestDO original,@Param("f") SeckillRefundRecordDO refund);

    @Insert("INSERT INTO promotion_seckill_refund_record(tenant_id,after_sale_id,request_no,order_id,order_item_id,member_id,activity_id,generation,redis_epoch,sku_id,"
            + "refund_no,refunded_count,restore_count,refund_amount,pay_refund_id,refund_time,after_sale_version,order_version,shipped,state,version,create_time,update_time) "
            + "VALUES(#{f.tenantId},#{f.afterSaleId},#{f.requestNo},#{f.orderId},#{f.orderItemId},#{f.memberId},#{f.activityId},#{f.generation},#{f.redisEpoch},#{f.skuId},"
            + "#{f.refundNo},#{f.refundedCount},#{f.restoreCount},#{f.refundAmount},#{f.payRefundId},#{f.refundTime},#{f.afterSaleVersion},#{f.orderVersion},#{f.shipped},#{f.state},0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
    int insert(@Param("f") SeckillRefundRecordDO refund);
}
