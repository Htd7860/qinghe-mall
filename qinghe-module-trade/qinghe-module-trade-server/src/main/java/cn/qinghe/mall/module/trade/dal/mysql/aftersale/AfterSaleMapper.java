package cn.qinghe.mall.module.trade.dal.mysql.aftersale;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.service.aftersale.dto.AfterSaleUnitPlan;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 原申请保存职责；所有占用调用必须先持有同一原订单父锁。
 * @author 芋道源码
 */
@Mapper
public interface AfterSaleMapper {
    @Select("SELECT COALESCE(SUM(goods_price),0) FROM trade_after_sale_unit WHERE tenant_id=#{tenant} AND order_id=#{order} AND state='RESERVED'")
    long reservedGoods(@Param("tenant") Long tenant,@Param("order") Long order);
    @Select("SELECT * FROM trade_after_sale WHERE tenant_id=#{tenant} AND user_id=#{user} AND request_id=#{request}")
    AfterSaleDO request(@Param("tenant") Long tenant,@Param("user") Long user,@Param("request") String request);
    @Select("SELECT COUNT(*) FROM trade_after_sale WHERE tenant_id=#{tenant} AND order_id=#{order} AND active_item_id IS NOT NULL")
    int pending(@Param("tenant") Long tenant,@Param("order") Long order);
    @Select("SELECT COUNT(*) FROM trade_after_sale WHERE tenant_id=#{tenant} AND active_item_id=#{item}")
    int activeItem(@Param("tenant") Long tenant,@Param("item") Long item);
    @Select("SELECT tenant_id,after_sale_id,order_id,order_item_id,unit_no,goods_price,original_delivery_share,state FROM trade_after_sale_unit "
        +"WHERE tenant_id=#{tenant} AND active_item_id=#{item} ORDER BY unit_no LIMIT 10000 FOR UPDATE")
    List<AfterSaleUnitDO> occupied(@Param("tenant") Long tenant,@Param("item") Long item);
    @Insert("INSERT INTO trade_after_sale(tenant_id,user_id,request_id,no,order_id,order_item_id,order_version,status,way,count,goods_refund_price,"
        +"original_delivery_share,apply_reason,apply_description,auth_version,create_time,update_time) VALUES(#{row.tenantId},#{row.userId},#{row.requestId},#{row.no},"
        +"#{row.orderId},#{row.orderItemId},#{row.orderVersion},10,#{row.way},#{row.count},#{row.goodsRefundPrice},#{row.originalDeliveryShare},#{row.applyReason},#{row.applyDescription},0,#{row.createTime},#{row.updateTime})")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insert(@Param("row") AfterSaleDO row);
    @Insert("<script>INSERT INTO trade_after_sale_unit(tenant_id,after_sale_id,order_id,order_item_id,unit_no,goods_price,original_delivery_share,state) VALUES "
        +"<foreach collection='units' item='unit' separator=','>(#{tenant},#{afterSale},#{order},#{item},#{unit.unitNo},#{unit.goodsPrice},#{unit.originalDeliveryShare},'RESERVED')</foreach></script>")
    int units(@Param("tenant") Long tenant,@Param("afterSale") Long afterSale,@Param("order") Long order,@Param("item") Long item,@Param("units") List<AfterSaleUnitPlan.Unit> units);
    @Update("UPDATE trade_order SET after_sale_pending_count=after_sale_pending_count+1,auth_version=auth_version+1,update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND id=#{order} AND user_id=#{user} AND status IN(10,20,30) AND pay_status=b'1' "
        +"AND after_sale_pending_count<200 AND auth_version=#{version} AND auth_version<9007199254740991")
    int occupyOrder(@Param("tenant") Long tenant,@Param("user") Long user,@Param("order") Long order,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_after_sale_log(tenant_id,after_sale_id,actor_id,actor_type,action,from_status,to_status,after_sale_version,create_time) "
        +"VALUES(#{tenant},#{id},#{actor},'MEMBER','MEMBER_APPLY',NULL,10,0,#{now})")
    int log(@Param("tenant") Long tenant,@Param("id") Long id,@Param("actor") Long actor,@Param("now") LocalDateTime now);
}
