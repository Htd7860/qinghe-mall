package cn.qinghe.mall.module.trade.dal.mysql.order;

import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 复用七个已有恢复动作的发现谓词；只登记原事件目标，不重启/清零既有任务。 */
@Mapper
public interface TradeOrderEventConsumeMapper {
    /** 提前到达仍只登记原付款期限；不消耗业务失败次数或提前关单。 */
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},o.id,'CANCEL_EXPIRED','READY',0,GREATEST(#{now},o.pay_deadline),0,#{now},#{now} FROM trade_order o "
        +"WHERE o.tenant_id=#{tenant} AND o.id=#{order} AND o.status=0 AND o.pay_status=b'0' "
        +"AND (o.type=0 OR (o.type=1 AND #{seckill})) "
        +"AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='CANCEL_EXPIRED')")
    int seedExpiry(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Select("SELECT * FROM trade_order_outbox WHERE tenant_id=#{tenant} AND event_id=#{event}")
    TradeOrderOutboxDO original(@Param("tenant") Long tenant,@Param("event") String event);
    @Insert("INSERT IGNORE INTO trade_order_event_receipt(tenant_id,event_id,consumer_code,payload_sha256,task_count,consumed_at) "
        +"VALUES(#{tenant},#{event},'TRADE_RECOVERY_V1',#{hash},0,#{now})")
    int receipt(@Param("tenant") Long tenant,@Param("event") String event,@Param("hash") String hash,@Param("now") LocalDateTime now);
    @Select("SELECT payload_sha256 FROM trade_order_event_receipt WHERE tenant_id=#{tenant} AND event_id=#{event} AND consumer_code='TRADE_RECOVERY_V1'")
    String receiptHash(@Param("tenant") Long tenant,@Param("event") String event);
    @Update("UPDATE trade_order_event_receipt SET task_count=#{count} WHERE tenant_id=#{tenant} AND event_id=#{event} AND consumer_code='TRADE_RECOVERY_V1'")
    int tasks(@Param("tenant") Long tenant,@Param("event") String event,@Param("count") int count);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},o.id,'PAY_SYNC','READY',0,#{now},0,#{now},#{now} FROM trade_order o WHERE o.tenant_id=#{tenant} "
        +"AND o.id=#{order} AND o.status=0 AND o.pay_status=b'0' AND o.pay_price>0 AND (o.type=0 OR (o.type=1 AND #{seckill})) AND NOT EXISTS(SELECT 1 FROM "
        +"trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='PAY_SYNC')")
    int seedPay(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},o.id,'CANCEL_EXPIRED','READY',0,#{now},0,#{now},#{now} FROM trade_order o WHERE "
        +"o.tenant_id=#{tenant} AND o.id=#{order} AND o.status=0 AND o.pay_status=b'0' AND o.pay_deadline<=#{now} AND (o.type=0 OR (o.type=1 AND #{seckill})) AND NOT "
        +"EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND "
        +"r.kind='CANCEL_EXPIRED')")
    int seedCancel(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},o.id,'CANCEL_RELEASE','READY',0,#{now},0,#{now},#{now} FROM trade_order o WHERE "
        +"o.tenant_id=#{tenant} AND o.id=#{order} AND o.status=40 AND o.pay_status=b'0' AND o.cancel_resources_released=b'0' "
        +"AND (o.type=0 OR (o.type=1 AND #{seckill})) "
        +"AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND "
        +"r.kind='CANCEL_RELEASE')")
    int seedRelease(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},o.id,'CREATE_COMPENSATE','READY',0,#{now},0,#{now},#{now} FROM trade_order_prepare o WHERE "
        +"o.tenant_id=#{tenant} AND o.id=#{order} AND (o.stage='COMPENSATING' OR (o.stage IN('PREPARED','PROCESSING') AND "
        +"o.prepare_deadline<=#{now})) AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND "
        +"r.order_id=o.id AND r.kind='CREATE_COMPENSATE')")
    int seedCompensate(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},o.id,'AUTO_RECEIVE','READY',0,#{now},0,#{now},#{now} FROM trade_order o WHERE "
        +"o.tenant_id=#{tenant} AND o.id=#{order} AND o.status=20 AND o.pay_status=b'1' AND o.after_sale_pending_count=0 AND "
        +"o.delivery_time<=#{now}-INTERVAL 14 DAY AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE "
        +"r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='AUTO_RECEIVE')")
    int seedReceive(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},a.order_id,'RETURN_EXPIRE','READY',0,#{now},0,#{now},#{now} FROM trade_after_sale a WHERE "
        +"a.id=#{afterSale} AND a.tenant_id=#{tenant} AND a.order_id=#{order} AND a.status=20 AND a.way=20 AND "
        +"a.return_deadline<=#{now} AND a.delivery_time IS NULL AND a.receive_time IS NULL AND NOT EXISTS(SELECT 1 FROM "
        +"trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=a.order_id AND r.kind='RETURN_EXPIRE')")
    int seedReturn(@Param("tenant") Long tenant,@Param("order") Long order,@Param("afterSale") Long afterSale,@Param("now") LocalDateTime now);
    @Insert("INSERT IGNORE INTO "
        +"trade_order_recovery(tenant_id,order_id,after_sale_id,kind,status,attempts,available_at,auth_version,create_time,update_time)"
        +" SELECT #{tenant},f.order_id,f.after_sale_id,'REFUND','READY',0,#{now},0,#{now},#{now} FROM trade_after_sale_refund"
        +" f WHERE f.after_sale_id=#{afterSale} AND f.tenant_id=#{tenant} AND f.order_id=#{order} AND "
        +"f.execution_requested_at IS NOT NULL AND (f.status<>'SUCCESS' OR f.stock_recovered=b'0' OR f.coupon_recovered=b'0')"
        +" AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=f.order_id AND "
        +"r.after_sale_id=f.after_sale_id AND r.kind='REFUND')")
    int seedRefund(@Param("tenant") Long tenant,@Param("order") Long order,@Param("afterSale") Long afterSale,@Param("now") LocalDateTime now);
}
