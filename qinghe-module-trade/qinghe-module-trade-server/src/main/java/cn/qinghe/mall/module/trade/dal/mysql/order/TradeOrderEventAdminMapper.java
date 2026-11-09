package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 同库事件/固定消费者查询与原号排队；不删除回执或修改业务事件内容。 */
@Mapper
public interface TradeOrderEventAdminMapper {
    String JOIN=" FROM trade_order_outbox o LEFT JOIN trade_order_event_receipt r ON r.tenant_id=o.tenant_id AND r.event_id=o.event_id AND r.consumer_code='TRADE_RECOVERY_V1' ";
    String FIELDS="SELECT o.event_id,o.preparation_id AS order_id,o.event_type,o.aggregate_version,o.schema_version,o.status,o.attempts,o.replay_version,o.last_error_code,o.created_at,o.available_at,o.published_at,o.lease_until,r.consumed_at,r.task_count";
    String FILTER=" WHERE o.tenant_id=#{tenant} <if test='body.status!=null'>AND o.status=#{body.status}</if> <if test='body.eventType!=null'>AND o.event_type=#{body.eventType}</if> <if test='body.orderId!=null'>AND o.preparation_id=#{body.orderId}</if> <if test='body.consumed!=null'>AND (r.consumed_at IS NOT NULL)=#{body.consumed}</if> ";
    @Select("<script>SELECT COUNT(*)"+JOIN+FILTER+"</script>")
    long count(@Param("tenant") Long tenant,@Param("body") TradeEventPageReqVO body);
    @Select("<script>"+FIELDS+JOIN+FILTER+"ORDER BY o.created_at DESC,o.event_id DESC LIMIT #{body.pageSize} OFFSET #{offset}</script>")
    List<TradeEventRespVO> page(@Param("tenant") Long tenant,@Param("body") TradeEventPageReqVO body,@Param("offset") long offset);
    @Select(FIELDS+JOIN+"WHERE o.tenant_id=#{tenant} AND o.event_id=#{event}")
    TradeEventRespVO get(@Param("tenant") Long tenant,@Param("event") String event);
    @Select("SELECT * FROM trade_order_outbox WHERE tenant_id=#{tenant} AND event_id=#{event} FOR UPDATE")
    TradeOrderOutboxDO lock(@Param("tenant") Long tenant,@Param("event") String event);
    @Select("SELECT COUNT(*) FROM trade_order_event_receipt WHERE tenant_id=#{tenant} AND event_id=#{event} AND consumer_code='TRADE_RECOVERY_V1'")
    long consumed(@Param("tenant") Long tenant,@Param("event") String event);
    @Select("SELECT request_id,event_id,actor_id,reason,from_version,to_version,previous_status,previous_attempts,previous_error_code,previous_published_at,create_time FROM trade_order_event_replay WHERE tenant_id=#{tenant} AND request_id=#{request}")
    TradeEventReplayRespVO replay(@Param("tenant") Long tenant,@Param("request") String request);
    @Select("SELECT COUNT(*) FROM trade_order_event_replay WHERE tenant_id=#{tenant} AND event_id=#{event}")
    long historyCount(@Param("tenant") Long tenant,@Param("event") String event);
    @Select("SELECT request_id,event_id,actor_id,reason,from_version,to_version,previous_status,previous_attempts,previous_error_code,previous_published_at,create_time FROM trade_order_event_replay WHERE tenant_id=#{tenant} AND event_id=#{event} ORDER BY to_version DESC LIMIT #{limit} OFFSET #{offset}")
    List<TradeEventReplayRespVO> history(@Param("tenant") Long tenant,@Param("event") String event,@Param("limit") int limit,@Param("offset") long offset);
    @Update("UPDATE trade_order_outbox SET status='PENDING',attempts=0,available_at=#{now},published_at=NULL,last_error_code=NULL,replay_version=replay_version+1 "
        +"WHERE tenant_id=#{tenant} AND event_id=#{event} AND replay_version=#{version} AND replay_version<9007199254740991 "
        +"AND lease_owner IS NULL AND lease_until IS NULL AND (status='MANUAL' OR (status='PUBLISHED' AND published_at<=#{now}-INTERVAL 1 MINUTE))")
    int requeue(@Param("tenant") Long tenant,@Param("event") String event,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_event_replay(tenant_id,request_id,event_id,actor_id,reason,from_version,to_version,previous_status,previous_attempts,previous_error_code,previous_published_at,create_time) "
        +"VALUES(#{tenant},#{row.requestId},#{row.eventId},#{row.actorId},#{row.reason},#{row.fromVersion},#{row.toVersion},#{row.previousStatus},#{row.previousAttempts},#{row.previousErrorCode},#{row.previousPublishedAt},#{row.createTime})")
    int insertReplay(@Param("tenant") Long tenant,@Param("row") TradeEventReplayRespVO row);
}
