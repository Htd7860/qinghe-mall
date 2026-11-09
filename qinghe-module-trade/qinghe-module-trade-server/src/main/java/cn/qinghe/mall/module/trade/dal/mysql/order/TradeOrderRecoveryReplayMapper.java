package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderRecoveryReplayDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TradeOrderRecoveryReplayMapper {
    @Select("SELECT * FROM trade_order_recovery_replay WHERE tenant_id=#{tenant} AND request_id=#{request}")
    TradeOrderRecoveryReplayDO find(@Param("tenant") Long tenant,@Param("request") String request);
    @Select("SELECT * FROM trade_order_recovery_replay WHERE tenant_id=#{tenant} AND recovery_id=#{id} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<TradeOrderRecoveryReplayDO> history(@Param("tenant") Long tenant,@Param("id") Long id,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM trade_order_recovery_replay WHERE tenant_id=#{tenant} AND recovery_id=#{id}")
    long countHistory(@Param("tenant") Long tenant,@Param("id") Long id);
    @Insert("INSERT INTO trade_order_recovery_replay(tenant_id,recovery_id,order_id,kind,request_id,actor_id,reason,from_version,to_version,previous_attempts,previous_error_code,"
        + "status,attempts,last_version,create_time,update_time) VALUES(#{row.tenantId},#{row.recoveryId},#{row.orderId},#{row.kind},#{row.requestId},#{row.actorId},#{row.reason},"
        + "#{row.fromVersion},#{row.toVersion},#{row.previousAttempts},#{row.previousErrorCode},'READY',0,#{row.toVersion},#{row.createTime},#{row.createTime})")
    int insert(@Param("row") TradeOrderRecoveryReplayDO row);
    @Update("UPDATE trade_order_recovery_replay SET status=#{state},attempts=#{attempts},last_error_code=#{error},last_job_id=#{job},trace_id=#{trace},"
        + "last_version=#{version}+1,update_time=#{now} WHERE tenant_id=#{tenant} AND recovery_id=#{id} AND request_id=#{request} "
        + "AND last_version=#{version} AND status IN('READY','RUNNING')")
    int progress(@Param("tenant") Long tenant,@Param("id") Long id,@Param("request") String request,@Param("version") Long version,
        @Param("state") String state,@Param("attempts") int attempts,@Param("error") Integer error,@Param("job") Long job,
        @Param("trace") String trace,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order_recovery_replay a JOIN trade_order_recovery r ON r.tenant_id=a.tenant_id AND r.id=a.recovery_id "
        + "AND r.active_replay_id=a.request_id SET a.status='MANUAL',a.attempts=r.attempts,a.last_error_code=r.last_error_code,"
        + "a.last_version=r.auth_version,a.update_time=#{now} WHERE a.tenant_id=#{tenant} AND r.tenant_id=#{tenant} "
        + "AND r.status='MANUAL' AND a.status='RUNNING' AND a.last_version<r.auth_version")
    int expired(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
}
