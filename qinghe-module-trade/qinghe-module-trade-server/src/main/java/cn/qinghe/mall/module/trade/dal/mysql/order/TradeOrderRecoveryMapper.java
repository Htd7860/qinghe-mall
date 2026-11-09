package cn.qinghe.mall.module.trade.dal.mysql.order;

import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderRecoveryDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 固定订单恢复SQL；tenant/kind/order唯一，短事务领取，不在持锁时RPC。 */
@Mapper
public interface TradeOrderRecoveryMapper {
    /** 只发现财务明确执行且资金/资源未完成的原售后；每笔独立行，不挤占同单其他售后。 */
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,after_sale_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},f.order_id,f.after_sale_id,'REFUND','READY',0,#{now},0,#{now},#{now} FROM trade_after_sale_refund f "
        +"WHERE f.tenant_id=#{tenant} AND f.execution_requested_at IS NOT NULL "
        +"AND (f.status<>'SUCCESS' OR f.stock_recovered=b'0' OR f.coupon_recovered=b'0') "
        +"AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=f.order_id "
        +"AND r.after_sale_id=f.after_sale_id AND r.kind='REFUND') ORDER BY f.create_time,f.after_sale_id LIMIT #{limit}")
    int seedRefund(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now);
    /** 一条恢复行始终指向真实原订单；同单多笔申请逐笔推进。 */
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        +"SELECT #{tenant},a.order_id,'RETURN_EXPIRE','READY',0,#{now},0,#{now},#{now} FROM trade_after_sale a "
        +"WHERE a.tenant_id=#{tenant} AND a.status=20 AND a.way=20 AND a.return_deadline<=#{now} AND a.delivery_time IS NULL AND a.receive_time IS NULL "
        +"AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=a.order_id AND r.kind='RETURN_EXPIRE') "
        +"GROUP BY a.order_id ORDER BY MIN(a.return_deadline),a.order_id LIMIT #{limit}")
    int seedReturn(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now);
    /** 后续新申请再到期只重启已完成行；执行中/失败/人工行不重置预算或审计。 */
    @Update("UPDATE trade_order_recovery r SET status='READY',attempts=0,available_at=#{now},active_replay_id=NULL,"
        +"last_error_code=NULL,last_job_id=NULL,trace_id=NULL,auth_version=auth_version+1,update_time=#{now} "
        +"WHERE r.tenant_id=#{tenant} AND r.kind='RETURN_EXPIRE' AND r.status='DONE' AND r.lease_owner IS NULL AND r.lease_until IS NULL "
        +"AND r.auth_version<9007199254740991 AND EXISTS(SELECT 1 FROM trade_after_sale a WHERE a.tenant_id=#{tenant} AND a.order_id=r.order_id "
        +"AND a.status=20 AND a.way=20 AND a.return_deadline<=#{now} AND a.delivery_time IS NULL AND a.receive_time IS NULL) ORDER BY r.id LIMIT #{limit}")
    int reopenReturn(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now);
    /** 发现首次发货满14天且未被售后暂停的原单；已登记动作由原恢复行继续推进。 */
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        + "SELECT #{tenant},o.id,'AUTO_RECEIVE','READY',0,#{now},0,#{now},#{now} FROM trade_order o "
        + "WHERE o.tenant_id=#{tenant} AND o.status=20 AND o.pay_status=b'1' AND o.after_sale_pending_count=0 "
        + "AND o.delivery_time<=#{now}-INTERVAL 14 DAY "
        + "AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='AUTO_RECEIVE') "
        + "ORDER BY o.delivery_time,o.id LIMIT #{limit}")
    int seedReceive(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_order_recovery WHERE tenant_id=#{tenant} AND id=#{id}")
    TradeOrderRecoveryDO get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_order_recovery WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    TradeOrderRecoveryDO lock(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("<script>SELECT COUNT(*) FROM trade_order_recovery WHERE tenant_id=#{tenant} "
        + "<if test='state!=null'>AND status=#{state}</if> <if test='kind!=null'>AND kind=#{kind}</if></script>")
    long countPage(@Param("tenant") Long tenant,@Param("state") String state,@Param("kind") String kind);
    @Select("<script>SELECT * FROM trade_order_recovery WHERE tenant_id=#{tenant} "
        + "<if test='state!=null'>AND status=#{state}</if> <if test='kind!=null'>AND kind=#{kind}</if> "
        + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    java.util.List<TradeOrderRecoveryDO> page(@Param("tenant") Long tenant,@Param("state") String state,@Param("kind") String kind,
        @Param("limit") int limit,@Param("offset") long offset);
    @Update("UPDATE trade_order_recovery SET status='READY',attempts=0,available_at=#{now},active_replay_id=#{request}, "
        + "last_error_code=NULL,auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status='MANUAL' AND lease_owner IS NULL AND lease_until IS NULL "
        + "AND auth_version=#{version} AND auth_version<9007199254740991")
    int requeue(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("request") String request,@Param("now") LocalDateTime now);

    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        + "SELECT #{tenant},o.id,'PAY_SYNC','READY',0,#{now},0,#{now},#{now} FROM trade_order o "
        + "WHERE o.tenant_id=#{tenant} AND o.status=0 AND o.pay_status=b'0' AND o.pay_price>0 "
        + "AND (o.type=0 OR (o.type=1 AND #{seckill})) "
        + "AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='PAY_SYNC') "
        + "ORDER BY o.id LIMIT #{limit}")
    int seedPay(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        + "SELECT #{tenant},o.id,'CANCEL_EXPIRED','READY',0,#{now},0,#{now},#{now} FROM trade_order o "
        + "WHERE o.tenant_id=#{tenant} AND o.status=0 AND o.pay_status=b'0' AND o.pay_deadline<=#{now} "
        + "AND (o.type=0 OR (o.type=1 AND #{seckill})) "
        + "AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='CANCEL_EXPIRED') "
        + "ORDER BY o.id LIMIT #{limit}")
    int seedCancel(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        + "SELECT #{tenant},o.id,'CANCEL_RELEASE','READY',0,#{now},0,#{now},#{now} FROM trade_order o "
        + "WHERE o.tenant_id=#{tenant} AND o.status=40 AND o.pay_status=b'0' AND o.cancel_resources_released=b'0' "
        + "AND (o.type=0 OR (o.type=1 AND #{seckill})) "
        + "AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='CANCEL_RELEASE') "
        + "ORDER BY o.id LIMIT #{limit}")
    int seedRelease(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now,@Param("seckill") boolean seckill);
    @Insert("INSERT IGNORE INTO trade_order_recovery(tenant_id,order_id,kind,status,attempts,available_at,auth_version,create_time,update_time) "
        + "SELECT #{tenant},o.id,'CREATE_COMPENSATE','READY',0,#{now},0,#{now},#{now} FROM trade_order_prepare o "
        + "WHERE o.tenant_id=#{tenant} AND (o.stage='COMPENSATING' OR (o.stage IN('PREPARED','PROCESSING') AND o.prepare_deadline<=#{now})) "
        + "AND NOT EXISTS(SELECT 1 FROM trade_order_recovery r WHERE r.tenant_id=#{tenant} AND r.order_id=o.id AND r.kind='CREATE_COMPENSATE') "
        + "ORDER BY o.id LIMIT #{limit}")
    int seedCompensate(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now);

    @Update("UPDATE trade_order_recovery SET status='MANUAL',lease_owner=NULL,lease_until=NULL,last_error_code=503,update_time=#{now},auth_version=auth_version+1 "
        + "WHERE tenant_id=#{tenant} AND status='RUNNING' AND attempts=10 AND lease_until<=#{now} AND auth_version<9007199254740991 LIMIT #{limit}")
    int exhaustExpiredLeases(@Param("tenant") Long tenant,@Param("limit") int limit,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_order_recovery WHERE tenant_id=#{tenant} AND attempts<10 "
        + "AND ((status='READY' AND available_at<=#{now}) OR (status='RUNNING' AND lease_until<=#{now})) "
        + "ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED")
    TradeOrderRecoveryDO next(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_order_recovery WHERE tenant_id=#{tenant} AND order_id=#{order} AND kind='CANCEL_EXPIRED' AND attempts<10 "
        +"AND ((status='READY' AND available_at<=#{now}) OR (status='RUNNING' AND lease_until<=#{now})) LIMIT 1 FOR UPDATE SKIP LOCKED")
    TradeOrderRecoveryDO nextExpiry(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_recovery SET status='RUNNING',attempts=attempts+1,lease_owner=#{owner},lease_until=#{until}, "
        + "last_job_id=#{job},trace_id=#{trace},auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND auth_version=#{version} AND auth_version<9007199254740991 AND attempts<10 "
        + "AND ((status='READY' AND available_at<=#{now}) OR (status='RUNNING' AND lease_until<=#{now}))")
    int claim(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("owner") String owner,
        @Param("until") LocalDateTime until,@Param("job") Long job,@Param("trace") String trace,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_recovery SET status=#{state},available_at=#{available},lease_owner=NULL,lease_until=NULL, "
        + "attempts=attempts-#{refundAttempt},last_error_code=#{error},auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status='RUNNING' AND lease_owner=#{owner} AND lease_until>#{now} "
        + "AND auth_version=#{version} AND auth_version<9007199254740991")
    int finish(@Param("tenant") Long tenant,@Param("id") Long id,@Param("owner") String owner,@Param("version") Long version,
        @Param("state") String state,@Param("available") LocalDateTime available,@Param("refundAttempt") int refundAttempt,
        @Param("error") Integer error,@Param("now") LocalDateTime now);
    @Select("SELECT COUNT(*) FROM trade_order_recovery WHERE tenant_id=#{tenant} AND status='MANUAL'")
    long manualCount(@Param("tenant") Long tenant);
}
