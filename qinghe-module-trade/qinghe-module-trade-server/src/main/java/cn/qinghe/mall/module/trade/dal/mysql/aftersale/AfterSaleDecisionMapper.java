package cn.qinghe.mall.module.trade.dal.mysql.aftersale;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 原售后状态条件更新/日志；调用前必须持有原订单父锁，顺序不可售后→订单倒置。 */
@Mapper
public interface AfterSaleDecisionMapper {
    /** 只取告警所需编号与阶段，实际联系人/原因/地址不进入技术日志。 */
    @Options(timeout=3)
    @Select("SELECT id,order_id,status FROM trade_after_sale WHERE tenant_id=#{tenant} AND "
        +"((status=10 AND create_time<=#{now}-INTERVAL 48 HOUR) OR (status=30 AND delivery_time<=#{now}-INTERVAL 48 HOUR) "
        +"OR (status=35 AND receive_time<=#{now}-INTERVAL 48 HOUR)) "
        +"ORDER BY CASE status WHEN 10 THEN create_time WHEN 30 THEN delivery_time ELSE receive_time END,id LIMIT 21")
    List<AfterSaleDO> overdue(@Param("tenant") Long tenant,@Param("now") LocalDateTime now);
    /** 已持有原订单父锁；只领取尚未寄回且原期限已到的申请。 */
    @Select("SELECT * FROM trade_after_sale WHERE tenant_id=#{tenant} AND order_id=#{order} AND status=20 AND way=20 "
        +"AND return_deadline<=#{now} AND delivery_time IS NULL AND receive_time IS NULL ORDER BY return_deadline,id LIMIT 1 FOR UPDATE")
    AfterSaleDO nextExpired(@Param("tenant") Long tenant,@Param("order") Long order,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale SET status=64,auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        +"AND status=20 AND way=20 AND return_deadline<=#{now} AND delivery_time IS NULL AND receive_time IS NULL "
        +"AND auth_version=#{version} AND auth_version<9007199254740991")
    int expire(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_after_sale WHERE tenant_id=#{tenant} AND id=#{id}")
    AfterSaleDO get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_after_sale WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    AfterSaleDO lock(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_after_sale_log WHERE tenant_id=#{tenant} AND request_id=#{request}")
    AfterSaleLogDO receipt(@Param("tenant") Long tenant,@Param("request") String request);
    @Select("SELECT tenant_id,after_sale_id,order_id,order_item_id,unit_no,goods_price,original_delivery_share,state FROM trade_after_sale_unit "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} ORDER BY unit_no LIMIT 10000 FOR UPDATE")
    List<AfterSaleUnitDO> units(@Param("tenant") Long tenant,@Param("id") Long id);
    /** 退款事实RR投影专用，不在只读快照中加行锁。 */
    @Select("SELECT tenant_id,after_sale_id,order_id,order_item_id,unit_no,goods_price,original_delivery_share,state FROM trade_after_sale_unit "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} ORDER BY unit_no LIMIT 10000")
    List<AfterSaleUnitDO> refundedUnits(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_after_sale SET status=#{to},auth_version=auth_version+1,update_time=#{now},"
        +"audit_user_id=CASE WHEN #{audit} THEN #{actor} ELSE audit_user_id END,"
        +"audit_time=CASE WHEN #{audit} THEN #{now} ELSE audit_time END,"
        +"audit_reason=CASE WHEN #{audit} THEN #{reason} ELSE audit_reason END,"
        +"quality_approved=CASE WHEN #{audit} THEN #{quality} ELSE quality_approved END,"
        +"return_deadline=CASE WHEN #{audit} THEN #{deadline} ELSE return_deadline END "
        +",return_address=CASE WHEN #{audit} THEN #{address} ELSE return_address END "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND status=#{from} AND auth_version=#{version} AND auth_version<9007199254740991 "
        +"AND ((#{audit}=b'1' AND #{from}=10 AND #{to} IN(20,40,62)) OR (#{audit}=b'0' AND (#{from} IN(10,20) OR (#{from}=40 AND way=10)) AND #{to}=61 AND delivery_time IS NULL AND receive_time IS NULL))")
    int transition(@Param("tenant") Long tenant,@Param("id") Long id,@Param("from") Integer from,@Param("to") Integer to,@Param("version") Long version,
        @Param("audit") boolean audit,@Param("actor") Long actor,@Param("reason") String reason,@Param("quality") boolean quality,
        @Param("deadline") LocalDateTime deadline,@Param("address") String address,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale SET status=30,auth_version=auth_version+1,update_time=#{now},logistics_id=#{carrier},logistics_code=#{code},"
        +"logistics_name=#{name},logistics_no=#{tracking},delivery_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} AND status=20 AND way=20 "
        +"AND return_address IS NOT NULL AND return_deadline>#{now} AND auth_version=#{version} AND auth_version<9007199254740991")
    int deliver(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("carrier") Long carrier,
        @Param("code") String code,@Param("name") String name,@Param("tracking") String tracking,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale SET status=#{to},auth_version=auth_version+1,update_time=#{now},"
        +"receive_time=COALESCE(receive_time,#{now}),receive_user_id=#{actor},received_count=#{received},salable_count=#{salable},receive_reason=#{reason} "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND status=#{from} AND status IN(30,35) AND way=20 AND delivery_time IS NOT NULL "
        +"AND auth_version=#{version} AND auth_version<9007199254740991 AND #{received} BETWEEN received_count AND 9999 "
        +"AND #{salable} BETWEEN 0 AND #{received} AND (#{to}=35 OR (#{to}=40 AND #{received}=count))")
    int inspect(@Param("tenant") Long tenant,@Param("id") Long id,@Param("from") Integer from,@Param("to") Integer to,
        @Param("version") Long version,@Param("actor") Long actor,@Param("received") Integer received,
        @Param("salable") Integer salable,@Param("reason") String reason,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale_unit SET state='RELEASED' WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND state='RESERVED'")
    int release(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_order SET after_sale_pending_count=after_sale_pending_count-#{decrement},auth_version=auth_version+1,update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND status IN(10,20,30) AND pay_status=b'1' AND after_sale_pending_count>0 "
        +"AND #{decrement} IN(0,1) AND auth_version=#{version} AND auth_version<9007199254740991")
    int parent(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("decrement") int decrement,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_after_sale_log(tenant_id,after_sale_id,actor_id,actor_type,action,from_status,to_status,after_sale_version,create_time,request_id,reason,quality_approved,details) "
        +"VALUES(#{tenant},#{id},#{actor},#{actorType},#{action},#{from},#{to},#{version},#{now},#{request},#{reason},#{quality},#{details})")
    int log(@Param("tenant") Long tenant,@Param("id") Long id,@Param("actor") Long actor,@Param("actorType") String actorType,@Param("action") String action,
        @Param("from") Integer from,@Param("to") Integer to,@Param("version") Long version,@Param("now") LocalDateTime now,
        @Param("request") String request,@Param("reason") String reason,@Param("quality") boolean quality,@Param("details") String details);
}
