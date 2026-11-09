package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillOutboxDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 直接择取现有TradeOrderOutboxMapper租约/CAS，不改变原申请或重新生成事件号。 */
@Mapper
public interface SeckillOutboxPublishMapper {
    @Select("SELECT UTC_TIMESTAMP(3)") LocalDateTime now();
    @Select("SELECT * FROM promotion_seckill_outbox WHERE tenant_id=#{tenant} AND event_id=#{event}")
    SeckillOutboxDO original(@Param("tenant") Long tenant,@Param("event") String event);
    @Update("UPDATE promotion_seckill_outbox SET status='MANUAL',lease_owner=NULL,lease_until=NULL,last_error_code=503 "
            + "WHERE tenant_id=#{tenant} AND status='SENDING' AND attempts=10 AND lease_until<=#{now} LIMIT 20")
    int exhaust(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM promotion_seckill_outbox WHERE tenant_id=#{tenant} AND attempts<10 "
            + "AND ((status='PENDING' AND available_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now})) "
            + "ORDER BY available_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    SeckillOutboxDO next(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
    @Update("UPDATE promotion_seckill_outbox SET status='SENDING',attempts=attempts+1,lease_owner=#{owner},lease_until=#{until},last_error_code=NULL "
            + "WHERE tenant_id=#{tenant} AND event_id=#{event} AND attempts=#{before} AND attempts<10 "
            + "AND ((status='PENDING' AND available_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now}))")
    int claim(@Param("tenant") Long tenant,@Param("event") String event,@Param("before") Integer before,@Param("owner") String owner,
            @Param("until") LocalDateTime until,@Param("now") LocalDateTime now);
    @Update("UPDATE promotion_seckill_outbox SET status='PUBLISHED',published_at=#{now},lease_owner=NULL,lease_until=NULL,last_error_code=NULL "
            + "WHERE tenant_id=#{tenant} AND event_id=#{event} AND status='SENDING' AND lease_owner=#{owner} AND lease_until>#{now}")
    int published(@Param("tenant") Long tenant,@Param("event") String event,@Param("owner") String owner,@Param("now") LocalDateTime now);
    @Update("UPDATE promotion_seckill_outbox SET status=CASE WHEN attempts=10 THEN 'MANUAL' ELSE 'PENDING' END,available_at=#{available},"
            + "lease_owner=NULL,lease_until=NULL,last_error_code=503 WHERE tenant_id=#{tenant} AND event_id=#{event} "
            + "AND status='SENDING' AND lease_owner=#{owner} AND lease_until>#{now}")
    int failed(@Param("tenant") Long tenant,@Param("event") String event,@Param("owner") String owner,
            @Param("available") LocalDateTime available,@Param("now") LocalDateTime now);
    @Select("SELECT COUNT(*) FROM promotion_seckill_outbox WHERE tenant_id=#{tenant} AND status='MANUAL'")
    long manual(@Param("tenant") Long tenant);
    @Select("SELECT COUNT(*) FROM promotion_seckill_outbox WHERE tenant_id=#{tenant} AND status<>'PUBLISHED' AND created_at<=#{now}-INTERVAL 1 MINUTE")
    long overdue(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
}
