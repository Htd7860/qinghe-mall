package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderItemDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 择取原订单状态条件更新；订单父锁统一串行化发货、收货与后续售后占用。 */
@Mapper
public interface TradeOrderFulfillmentMapper {
    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    TradeOrderDO lock(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_order_item WHERE tenant_id=#{tenant} AND order_id=#{id} ORDER BY id LIMIT 201 FOR UPDATE")
    List<TradeOrderItemDO> items(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_order_item SET delivery_count=count-refund_count WHERE tenant_id=#{tenant} AND order_id=#{id} AND delivery_count=0")
    int deliveryItems(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_order SET status=20,logistics_id=#{carrier},logistics_code=#{code},logistics_name=#{name},logistics_no=#{tracking},"
        + "delivery_time=#{now},auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        + "AND status=10 AND pay_status=b'1' AND after_sale_pending_count=0 AND auth_version=#{version} AND auth_version<9007199254740991")
    int deliver(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("carrier") Long carrier,@Param("code") String code,@Param("name") String name,
        @Param("tracking") String tracking,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET logistics_id=#{carrier},logistics_code=#{code},logistics_name=#{name},logistics_no=#{tracking},"
        + "auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} AND status IN(20,30) "
        + "AND pay_status=b'1' AND auth_version=#{version} AND auth_version<9007199254740991")
    int correct(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("carrier") Long carrier,@Param("code") String code,@Param("name") String name,
        @Param("tracking") String tracking,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET status=30,receive_time=#{now},auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status=20 AND pay_status=b'1' AND after_sale_pending_count=0 "
        + "AND delivery_time<=#{now} AND auth_version=#{version} AND auth_version<9007199254740991 "
        + "AND (#{automatic}=b'0' OR delivery_time<=#{now}-INTERVAL 14 DAY)")
    int receive(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("automatic") boolean automatic,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_log(tenant_id,order_id,actor_id,actor_type,action,from_status,to_status,order_version,details,create_time) "
        + "VALUES(#{tenant},#{id},#{actor},#{actorType},#{action},#{from},#{to},#{version},#{details},#{now})")
    int log(@Param("tenant") Long tenant,@Param("id") Long id,@Param("actor") Long actor,@Param("actorType") String actorType,
        @Param("action") String action,@Param("from") Integer from,@Param("to") Integer to,@Param("version") Long version,
        @Param("details") String details,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_outbox(event_id,tenant_id,preparation_id,event_type,aggregate_version,schema_version,payload,status,attempts,created_at,available_at) "
        + "VALUES(#{event},#{tenant},#{order},#{type},#{version},1,CAST(#{payload} AS JSON),'PENDING',0,#{now},#{now})")
    int event(@Param("event") String event,@Param("tenant") Long tenant,@Param("order") Long order,@Param("type") String type,
        @Param("version") Long version,@Param("payload") String payload,@Param("now") LocalDateTime now);
}
