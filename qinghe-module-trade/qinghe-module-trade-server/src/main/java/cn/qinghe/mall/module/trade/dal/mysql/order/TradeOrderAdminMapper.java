package cn.qinghe.mall.module.trade.dal.mysql.order;

import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 择取原租户订单分页/批量原项/操作历史；所有查询显式固定当前租户。
 * @author 芋道源码
 */
@Mapper
public interface TradeOrderAdminMapper {
    String SUMMARY="id,tenant_id,no,user_id,status,auth_version,product_count,total_price,discount_price,delivery_price,pay_price,refund_price,pay_status,"
        +"cancel_resources_released,after_sale_pending_count,logistics_id,logistics_name,logistics_no,create_time,pay_time,delivery_time,receive_time,cancel_time";
    String FILTER=" WHERE tenant_id=#{tenant} <if test='filter.id!=null'> AND id=#{filter.id} </if>"
        +"<if test='filter.no!=null'> AND no=#{filter.no} </if><if test='filter.status!=null'> AND status=#{filter.status} </if>";
    @Select("<script>SELECT COUNT(*) FROM trade_order"+FILTER+"</script>")
    long count(@Param("tenant") Long tenant,@Param("filter") TradeOrderPageReqVO filter);
    @Select("<script>SELECT "+SUMMARY+" FROM trade_order"+FILTER+" ORDER BY id DESC LIMIT #{filter.pageSize} OFFSET #{offset}</script>")
    List<TradeOrderDO> page(@Param("tenant") Long tenant,@Param("filter") TradeOrderPageReqVO filter,@Param("offset") long offset);
    @Select("SELECT "+SUMMARY+",receiver_name,receiver_mobile,receiver_area_id FROM trade_order WHERE tenant_id=#{tenant} AND id=#{id}")
    TradeOrderDO detail(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT "+SUMMARY+",receiver_name,receiver_mobile,receiver_area_id,receiver_detail_address,user_remark "
        +"FROM trade_order WHERE tenant_id=#{tenant} AND id=#{id} AND status=10 AND pay_status=b'1' AND after_sale_pending_count=0")
    TradeOrderDO deliveryDetail(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("<script>SELECT id,tenant_id,order_id,user_id,spu_id,spu_name,sku_id,properties,pic_url,count,refund_count,delivery_count,price,"
        +"goods_price,discount_price,delivery_price,pay_price FROM trade_order_item WHERE tenant_id=#{tenant} AND order_id IN "
        +"<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach> ORDER BY order_id,id LIMIT 4001</script>")
    List<TradeOrderItemDO> items(@Param("tenant") Long tenant,@Param("ids") List<Long> ids);
    @Select("SELECT COUNT(*) FROM trade_order_log WHERE tenant_id=#{tenant} AND order_id=#{id}")
    long logCount(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_order_log WHERE tenant_id=#{tenant} AND order_id=#{id} ORDER BY order_version DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<TradeOrderLogDO> logs(@Param("tenant") Long tenant,@Param("id") Long id,@Param("limit") int limit,@Param("offset") long offset);
}
