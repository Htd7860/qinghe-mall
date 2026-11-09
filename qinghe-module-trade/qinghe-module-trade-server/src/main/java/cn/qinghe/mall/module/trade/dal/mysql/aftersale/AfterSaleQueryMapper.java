package cn.qinghe.mall.module.trade.dal.mysql.aftersale;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSalePageReqVO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 择取原分页/本人归属/日志查询。user仅由可信会员入口赋值，商家入口固定null。
 * @author 芋道源码
 */
@Mapper
public interface AfterSaleQueryMapper {
    String OWNER=" WHERE tenant_id=#{tenant} <if test='user!=null'> AND user_id=#{user} </if>";
    String FILTER=OWNER+"<if test='filter.id!=null'> AND id=#{filter.id} </if><if test='filter.no!=null'> AND no=#{filter.no} </if>"
        +"<if test='filter.orderId!=null'> AND order_id=#{filter.orderId} </if><if test='filter.status!=null'> AND status=#{filter.status} </if>"
        +"<if test='filter.way!=null'> AND way=#{filter.way} </if>"
        +"<if test='filter.overdueOnly'> AND ((status=10 AND create_time&lt;=#{now}-INTERVAL 48 HOUR) "
        +"OR (status=30 AND delivery_time&lt;=#{now}-INTERVAL 48 HOUR) OR (status=35 AND receive_time&lt;=#{now}-INTERVAL 48 HOUR)) </if>";
    String SUMMARY="id,tenant_id,user_id,no,order_id,order_item_id,status,way,count,goods_refund_price,auth_version,create_time,update_time,return_deadline,delivery_time,receive_time";
    @Select("<script>SELECT COUNT(*) FROM trade_after_sale"+FILTER+"</script>")
    long count(@Param("tenant") Long tenant,@Param("user") Long user,@Param("filter") AfterSalePageReqVO filter,@Param("now") LocalDateTime now);
    @Select("<script>SELECT "+SUMMARY+" FROM trade_after_sale"+FILTER+" ORDER BY id DESC LIMIT #{filter.pageSize} OFFSET #{offset}</script>")
    List<AfterSaleDO> page(@Param("tenant") Long tenant,@Param("user") Long user,@Param("filter") AfterSalePageReqVO filter,@Param("now") LocalDateTime now,@Param("offset") long offset);
    @Select("<script>SELECT * FROM trade_after_sale"+OWNER+" AND id=#{id}</script>")
    AfterSaleDO get(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id);
    @Select("SELECT * FROM trade_after_sale WHERE tenant_id=#{tenant} AND user_id=#{user} AND request_id=#{request}")
    AfterSaleDO application(@Param("tenant") Long tenant,@Param("user") Long user,@Param("request") String request);
    @Select("<script>SELECT id,tenant_id,user_id,order_id,sku_id,spu_name,pic_url FROM trade_order_item WHERE tenant_id=#{tenant} AND id IN "
        +"<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach> LIMIT 21</script>")
    List<TradeOrderItemDO> items(@Param("tenant") Long tenant,@Param("ids") List<Long> ids);
    @Select("<script>SELECT id,tenant_id,user_id,no FROM trade_order WHERE tenant_id=#{tenant} AND id IN "
        +"<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach> LIMIT 21</script>")
    List<TradeOrderDO> orders(@Param("tenant") Long tenant,@Param("ids") List<Long> ids);
    @Select("SELECT COUNT(*) FROM trade_after_sale_log WHERE tenant_id=#{tenant} AND after_sale_id=#{id}")
    long logCount(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_after_sale_log WHERE tenant_id=#{tenant} AND after_sale_id=#{id} ORDER BY after_sale_version DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<AfterSaleLogDO> logs(@Param("tenant") Long tenant,@Param("id") Long id,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT l.* FROM trade_after_sale_log l JOIN trade_after_sale a ON a.tenant_id=l.tenant_id AND a.id=l.after_sale_id "
        +"WHERE l.tenant_id=#{tenant} AND l.actor_id=#{actor} AND l.actor_type=#{type} AND l.request_id=#{request} "
        +"AND (#{type}='ADMIN' OR a.user_id=#{actor})")
    AfterSaleLogDO receipt(@Param("tenant") Long tenant,@Param("actor") Long actor,@Param("type") String type,@Param("request") String request);
}
