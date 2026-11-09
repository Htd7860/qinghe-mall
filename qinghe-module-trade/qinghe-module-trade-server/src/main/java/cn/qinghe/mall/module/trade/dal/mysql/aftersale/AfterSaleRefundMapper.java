package cn.qinghe.mall.module.trade.dal.mysql.aftersale;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleRefundDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.qinghe.mall.module.trade.service.aftersale.dto.AfterSaleRefundBalanceDTO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 原退款号/金额账职责；全部写入先持原订单父锁，不在此访问支付或库存。 */
@Mapper
public interface AfterSaleRefundMapper {
    @Select("SELECT * FROM trade_after_sale_refund WHERE tenant_id=#{tenant} AND after_sale_id=#{id}")
    AfterSaleRefundDO get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_after_sale_refund SET execution_actor_id=#{actor},execution_requested_at=#{now},update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND status='PREPARED' AND pay_refund_id IS NULL "
        +"AND execution_actor_id IS NULL AND execution_requested_at IS NULL")
    int requestExecution(@Param("tenant") Long tenant,@Param("id") Long id,@Param("actor") Long actor,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale SET auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        +"AND status=45 AND refund_time IS NULL AND auth_version=#{version} AND auth_version<9007199254740991")
    int executionCase(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        +"AND pay_status=b'1' AND status IN(10,20,30) AND auth_version=#{version} AND auth_version<9007199254740991")
    int executionOrder(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_order_item WHERE tenant_id=#{tenant} AND order_id=#{order} AND id=#{id}")
    TradeOrderItemDO originalItem(@Param("tenant") Long tenant,@Param("order") Long order,@Param("id") Long id);
    @Update("UPDATE trade_after_sale_refund SET stock_recovered=b'1',stock_receipt=CAST(#{receipt} AS JSON),update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND status='SUCCESS' AND restore_count>0 AND stock_recovered=b'0'")
    int stockRecovered(@Param("tenant") Long tenant,@Param("id") Long id,@Param("receipt") String receipt,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale_refund SET coupon_recovered=b'1',coupon_receipt=CAST(#{receipt} AS JSON),update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND status='SUCCESS' AND coupon_return_required=b'1' AND coupon_recovered=b'0'")
    int couponRecovered(@Param("tenant") Long tenant,@Param("id") Long id,@Param("receipt") String receipt,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale SET auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        +"AND status=50 AND refund_time IS NOT NULL AND auth_version=#{version} AND auth_version<9007199254740991")
    int resourceCase(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        +"AND pay_status=b'1' AND status IN(10,20,30,40) AND (status<>40 OR cancel_type=20) "
        +"AND auth_version=#{version} AND auth_version<9007199254740991")
    int resourceOrder(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale_refund SET status=#{status},pay_refund_id=COALESCE(pay_refund_id,#{pay}),pay_refund_version=#{remoteVersion},update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND status<>'SUCCESS' AND execution_requested_at IS NOT NULL "
        +"AND #{status} IN('PREPARED','PROCESSING','UNKNOWN','FAILED') AND (pay_refund_id IS NULL OR pay_refund_id=#{pay}) "
        +"AND (pay_refund_version IS NULL OR pay_refund_version<=#{remoteVersion})")
    int progress(@Param("tenant") Long tenant,@Param("id") Long id,@Param("pay") Long pay,@Param("remoteVersion") Long remoteVersion,
        @Param("status") String status,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale_refund SET status='SUCCESS',pay_refund_id=#{pay},pay_refund_version=#{remoteVersion},refund_time=#{refunded},"
        +"stock_recovered=(restore_count=0),coupon_recovered=(coupon_return_required=b'0'),update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND status<>'SUCCESS' AND execution_requested_at IS NOT NULL AND (pay_refund_id IS NULL OR pay_refund_id=#{pay}) "
        +"AND ((total_price=0 AND #{pay} IS NULL AND #{remoteVersion} IS NULL) OR (total_price>0 AND #{pay} IS NOT NULL "
        +"AND (pay_refund_version IS NULL OR pay_refund_version<=#{remoteVersion})))")
    int success(@Param("tenant") Long tenant,@Param("id") Long id,@Param("pay") Long pay,@Param("remoteVersion") Long remoteVersion,
        @Param("refunded") LocalDateTime refunded,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_after_sale_unit SET state='REFUNDED' WHERE tenant_id=#{tenant} AND after_sale_id=#{id} AND state='RESERVED'")
    int refundUnits(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_order_item SET refund_count=refund_count+#{quantity} WHERE tenant_id=#{tenant} AND order_id=#{order} AND id=#{id} "
        +"AND refund_count=#{before} AND refund_count+#{quantity}<=count")
    int refundItem(@Param("tenant") Long tenant,@Param("order") Long order,@Param("id") Long id,@Param("before") Integer before,@Param("quantity") Integer quantity);
    @Update("UPDATE trade_after_sale SET status=50,refund_time=#{refunded},auth_version=auth_version+1,update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND status=45 AND refund_time IS NULL AND auth_version=#{version} AND auth_version<9007199254740991")
    int finish(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("refunded") LocalDateTime refunded,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET refund_price=refund_price+#{total},delivery_refund_price=delivery_refund_price+#{delivery},"
        +"delivery_refund_reserved=delivery_refund_reserved-#{delivery},after_sale_pending_count=after_sale_pending_count-1,"
        +"status=CASE WHEN #{closed} THEN 40 ELSE status END,cancel_type=CASE WHEN #{closed} THEN 20 ELSE cancel_type END,"
        +"cancel_time=CASE WHEN #{closed} THEN #{now} ELSE cancel_time END,auth_version=auth_version+1,update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND status IN(10,20,30) AND pay_status=b'1' AND after_sale_pending_count>0 "
        +"AND (#{closed}=b'0' OR after_sale_pending_count=1) AND delivery_refund_reserved=#{delivery} "
        +"AND refund_price+#{total}<=pay_price AND delivery_refund_price+#{delivery}<=delivery_price "
        +"AND auth_version=#{version} AND auth_version<9007199254740991")
    int confirmOrder(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("total") Long total,
        @Param("delivery") Long delivery,@Param("closed") boolean closed,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_after_sale_refund WHERE tenant_id=#{tenant} AND request_id=#{request}")
    AfterSaleRefundDO request(@Param("tenant") Long tenant,@Param("request") String request);
    @Select("SELECT * FROM trade_after_sale_refund WHERE tenant_id=#{tenant} AND active_order_id=#{order}")
    AfterSaleRefundDO inFlight(@Param("tenant") Long tenant,@Param("order") Long order);
    @Select("SELECT COALESCE(SUM(CASE WHEN status='SUCCESS' THEN goods_price ELSE 0 END),0) success_goods,"
        +"COALESCE(SUM(CASE WHEN status='SUCCESS' THEN delivery_price ELSE 0 END),0) success_delivery,"
        +"COALESCE(SUM(CASE WHEN status<>'SUCCESS' THEN delivery_price ELSE 0 END),0) pending_delivery "
        +"FROM trade_after_sale_refund WHERE tenant_id=#{tenant} AND order_id=#{order}")
    AfterSaleRefundBalanceDTO balance(@Param("tenant") Long tenant,@Param("order") Long order);
    @Select("SELECT order_item_id,state,COUNT(*) quantity,COALESCE(SUM(goods_price),0) goods_price FROM trade_after_sale_unit "
        +"WHERE tenant_id=#{tenant} AND order_id=#{order} AND state IN('RESERVED','REFUNDED') GROUP BY order_item_id,state LIMIT 401")
    List<AfterSaleRefundBalanceDTO.Units> unitBalances(@Param("tenant") Long tenant,@Param("order") Long order);
    @Insert("INSERT INTO trade_after_sale_refund(tenant_id,after_sale_id,order_id,order_item_id,user_id,pay_order_id,request_id,refund_no,actor_id,reason,"
        +"after_sale_version,status,goods_price,delivery_price,total_price,restore_count,coupon_return_required,shipped,create_time,update_time) "
        +"VALUES(#{row.tenantId},#{row.afterSaleId},#{row.orderId},#{row.orderItemId},#{row.userId},#{row.payOrderId},#{row.requestId},#{row.refundNo},#{row.actorId},#{row.reason},"
        +"#{row.afterSaleVersion},'PREPARED',#{row.goodsPrice},#{row.deliveryPrice},#{row.totalPrice},#{row.restoreCount},#{row.couponReturnRequired},#{row.shipped},#{row.createTime},#{row.updateTime})")
    int insert(@Param("row") AfterSaleRefundDO row);
    @Update("UPDATE trade_after_sale SET status=45,auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
        +"AND status=40 AND audit_user_id IS NOT NULL AND auth_version=#{version} AND auth_version<9007199254740991 "
        +"AND (way=10 OR (way=20 AND delivery_time IS NOT NULL AND receive_time IS NOT NULL AND received_count=count))")
    int begin(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET delivery_refund_reserved=delivery_refund_reserved+#{delivery},auth_version=auth_version+1,update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND status IN(10,20,30) AND pay_status=b'1' AND after_sale_pending_count>0 "
        +"AND auth_version=#{version} AND auth_version<9007199254740991 AND delivery_refund_reserved=0 "
        +"AND delivery_refund_price+#{delivery}<=delivery_price AND refund_price+#{reservedGoods}+#{delivery}<=pay_price")
    int reserve(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("delivery") Long delivery,
        @Param("reservedGoods") long reservedGoods,@Param("now") LocalDateTime now);
}
