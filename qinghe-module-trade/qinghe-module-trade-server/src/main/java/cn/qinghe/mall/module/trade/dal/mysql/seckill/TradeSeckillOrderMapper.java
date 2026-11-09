package cn.qinghe.mall.module.trade.dal.mysql.seckill;

import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillInboxDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 复用准备主从表自增序列；以下写操作只在会员→原申请→原报价的短事务内执行。 */
@Mapper
public interface TradeSeckillOrderMapper {
    @Select("SELECT * FROM trade_seckill_inbox WHERE tenant_id=#{tenant} AND event_id=#{event}")
    TradeSeckillInboxDO receipt(@Param("tenant") Long tenant,@Param("event") String event);

    /** 已完成的source=1记录仅分配共享ID和保留引用；不走普通库存、券及购物车阶段。 */
    @Insert("INSERT INTO trade_order_prepare(tenant_id,user_id,source_type,request_id,request_hash,quote_id,no,stage,auth_version,remark,snapshot,"
            + "product_count,total_price,discount_price,delivery_price,pay_price,prepare_deadline,created_at,updated_at) "
            + "VALUES(#{r.tenantId},#{r.userId},1,#{r.requestId},#{r.requestHash},#{r.quoteId},#{r.no},'CREATED',0,'',CAST(#{r.snapshot} AS JSON),"
            + "#{r.productCount},#{r.totalPrice},0,#{r.deliveryPrice},#{r.payPrice},#{r.prepareDeadline},#{r.createdAt},#{r.updatedAt})")
    @Options(useGeneratedKeys=true,keyProperty="r.id")
    int reserveOrderId(@Param("r") TradeOrderPrepareDO row);

    @Update("UPDATE trade_seckill_order_binding SET state=10,order_id=#{order},fence_version=fence_version+1,updated_at=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND request_no=#{request} AND state=0 AND order_id IS NULL "
            + "AND fence_version=#{fence} AND fence_version<9007199254740991 AND UTC_TIMESTAMP(3)<#{until}")
    int created(@Param("tenant") Long tenant,@Param("request") String request,@Param("order") Long order,
            @Param("fence") Long fence,@Param("until") LocalDateTime reconcileDeadline);

    @Update("UPDATE trade_seckill_quote SET bound_order_id=#{order},snapshot_cipher=NULL WHERE tenant_id=#{tenant} "
            + "AND member_id=#{member} AND quote_id=#{quote} AND bound_request_no=#{request} AND bound_order_id IS NULL AND snapshot_cipher IS NOT NULL")
    int attachQuote(@Param("tenant") Long tenant,@Param("member") Long member,@Param("quote") String quote,
            @Param("request") String request,@Param("order") Long order);

    @Select("SELECT event_id FROM trade_seckill_outbox WHERE tenant_id=#{tenant} AND request_no=#{request} "
            + "AND aggregate_version=#{fence} AND event_type=#{type}")
    String resultEvent(@Param("tenant") Long tenant,@Param("request") String request,@Param("fence") Long fence,@Param("type") String type);

    @Insert("INSERT INTO trade_seckill_outbox(tenant_id,event_id,request_no,event_type,aggregate_version,schema_version,payload,"
            + "status,attempts,available_at,replay_version,created_at) VALUES(#{tenant},#{event},#{request},#{type},#{fence},1,"
            + "CAST(#{payload} AS JSON),'PENDING',0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3))")
    int insertResult(@Param("tenant") Long tenant,@Param("event") String event,@Param("request") String request,
            @Param("type") String type,@Param("fence") Long fence,@Param("payload") String payload);

    @Insert("INSERT INTO trade_seckill_inbox(tenant_id,event_id,request_no,aggregate_version,event_type,payload_hash,result_state,order_id,result_event_id,processed_at) "
            + "VALUES(#{r.tenantId},#{r.eventId},#{r.requestNo},#{r.aggregateVersion},#{r.eventType},#{r.payloadHash},#{r.resultState},#{r.orderId},#{r.resultEventId},UTC_TIMESTAMP(3))")
    int complete(@Param("r") TradeSeckillInboxDO receipt);
}
