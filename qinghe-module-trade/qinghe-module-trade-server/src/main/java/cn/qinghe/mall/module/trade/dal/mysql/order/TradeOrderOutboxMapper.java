package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface TradeOrderOutboxMapper {
    @Select("SELECT o.id,o.tenant_id,o.create_time,o.pay_deadline FROM trade_order o WHERE o.tenant_id=#{tenant} AND o.status=0 AND o.pay_status=b'0' "
        +"AND NOT EXISTS(SELECT 1 FROM trade_order_outbox e WHERE e.tenant_id=#{tenant} AND e.preparation_id=o.id AND e.event_type='ORDER_EXPIRE_CHECK') ORDER BY o.id LIMIT 20")
    java.util.List<TradeOrderDO> expiryCandidates(@Param("tenant") Long tenant);
    @Insert("INSERT IGNORE INTO trade_order_outbox(event_id,tenant_id,preparation_id,event_type,aggregate_version,schema_version,payload,status,attempts,created_at,available_at) "
        +"SELECT #{event},o.tenant_id,o.id,'ORDER_EXPIRE_CHECK',0,1,CAST(#{payload} AS JSON),'PENDING',0,o.create_time,#{now} FROM trade_order o "
        +"WHERE o.tenant_id=#{tenant} AND o.id=#{order} AND o.status=0 AND o.pay_status=b'0' AND o.pay_deadline=#{deadline} "
        +"AND NOT EXISTS(SELECT 1 FROM trade_order_outbox e WHERE e.tenant_id=#{tenant} AND e.preparation_id=o.id AND e.event_type='ORDER_EXPIRE_CHECK')")
    int insertExpiry(@Param("tenant") Long tenant,@Param("order") Long order,@Param("event") String event,@Param("payload") String payload,
        @Param("deadline") LocalDateTime deadline,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_outbox SET status='MANUAL',lease_owner=NULL,lease_until=NULL,last_error_code=503 "
        +"WHERE tenant_id=#{tenant} AND status='SENDING' AND attempts=10 AND lease_until<=#{now} LIMIT 20")
    int exhaust(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_order_outbox WHERE tenant_id=#{tenant} AND attempts<10 AND "
        +"((status='PENDING' AND available_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now})) "
        +"ORDER BY available_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    TradeOrderOutboxDO next(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_outbox SET status='SENDING',attempts=attempts+1,lease_owner=#{owner},lease_until=#{until},last_error_code=NULL "
        +"WHERE tenant_id=#{tenant} AND event_id=#{id} AND attempts=#{before} AND attempts<10 AND "
        +"((status='PENDING' AND available_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now}))")
    int claim(@Param("tenant") Long tenant,@Param("id") String id,@Param("before") int before,@Param("owner") String owner,
        @Param("until") LocalDateTime until,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_outbox SET status='PUBLISHED',published_at=#{now},lease_owner=NULL,lease_until=NULL,last_error_code=NULL "
        +"WHERE tenant_id=#{tenant} AND event_id=#{id} AND status='SENDING' AND lease_owner=#{owner} AND lease_until>#{now}")
    int published(@Param("tenant") Long tenant,@Param("id") String id,@Param("owner") String owner,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_outbox SET status=CASE WHEN attempts=10 THEN 'MANUAL' ELSE 'PENDING' END,available_at=#{available},"
        +"lease_owner=NULL,lease_until=NULL,last_error_code=503 WHERE tenant_id=#{tenant} AND event_id=#{id} "
        +"AND status='SENDING' AND lease_owner=#{owner} AND lease_until>#{now}")
    int failed(@Param("tenant") Long tenant,@Param("id") String id,@Param("owner") String owner,
        @Param("available") LocalDateTime available,@Param("now") LocalDateTime now);
    @Select("SELECT COUNT(*) FROM trade_order_outbox WHERE tenant_id=#{tenant} AND status='MANUAL'")
    long manual(@Param("tenant") Long tenant);
    @Select("SELECT COUNT(*) FROM trade_order_outbox WHERE tenant_id=#{tenant} AND status<>'PUBLISHED' AND created_at<=#{now}-INTERVAL 1 MINUTE")
    long overdue(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
}
