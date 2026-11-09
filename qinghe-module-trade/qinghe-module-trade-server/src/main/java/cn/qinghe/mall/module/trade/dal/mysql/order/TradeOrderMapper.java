package cn.qinghe.mall.module.trade.dal.mysql.order;

import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderItemDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 原TradeOrderMapper/ItemMapper保存职责；所有归属与金额来自持久准备记录，非任意VO更新。
 * @author 芋道源码
 */
@Mapper
public interface TradeOrderMapper {
    @Select("<script>SELECT COUNT(*) FROM trade_order WHERE tenant_id=#{tenant} AND user_id=#{user} "
            + "<if test='state!=null'>AND status=#{state}</if></script>")
    long memberCount(@Param("tenant") Long tenant,@Param("user") Long user,@Param("state") Integer state);
    @Select("<script>SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND user_id=#{user} "
            + "<if test='state!=null'>AND status=#{state}</if> ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    java.util.List<TradeOrderDO> memberPage(@Param("tenant") Long tenant,@Param("user") Long user,@Param("state") Integer state,
            @Param("limit") int limit,@Param("offset") long offset);
    @Select("<script>SELECT * FROM trade_order_item WHERE tenant_id=#{tenant} AND user_id=#{user} AND order_id IN "
            + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach> ORDER BY order_id,id LIMIT 4001</script>")
    java.util.List<TradeOrderItemDO> memberItems(@Param("tenant") Long tenant,@Param("user") Long user,@Param("ids") java.util.List<Long> ids);

    @Update("UPDATE trade_order SET cancel_resources_released=b'1',auth_version=auth_version+1,update_time=#{now} "
            + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND status=40 AND pay_status=b'0' "
            + "AND cancel_type IN(10,30) AND cancel_time IS NOT NULL AND cancel_resources_released=b'0' "
            + "AND auth_version=#{version} AND auth_version<9007199254740991")
    int completeCancelResources(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,
            @Param("version") Long version,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_log(tenant_id,order_id,actor_id,actor_type,action,from_status,to_status,order_version,create_time) "
            + "VALUES(#{tenant},#{id},0,'SYSTEM','CANCEL_RELEASED',40,40,#{version},#{now})")
    int logCancelReleased(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE trade_order SET status=40,cancel_type=#{type},cancel_time=#{now},pay_order_id=#{pay},auth_version=auth_version+1,update_time=#{now} "
            + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND status=0 AND pay_status=b'0' "
            + "AND (pay_order_id IS NULL OR pay_order_id=#{pay}) AND auth_version=#{version} AND auth_version<9007199254740991 "
            + "AND (#{type}=30 OR (#{type}=10 AND pay_deadline<=#{now}))")
    int cancelUnpaid(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("pay") Long pay,
            @Param("type") Integer type,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_log(tenant_id,order_id,actor_id,actor_type,action,from_status,to_status,order_version,create_time) "
            + "VALUES(#{tenant},#{id},#{actor},#{actorType},#{action},0,40,#{version},#{now})")
    int logCancelled(@Param("tenant") Long tenant,@Param("id") Long id,@Param("actor") Long actor,
            @Param("actorType") String actorType,@Param("action") String action,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE trade_order SET status=10,pay_status=b'1',pay_order_id=#{pay},pay_time=#{paid},auth_version=auth_version+1,update_time=#{now} "
            + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND status=0 AND pay_status=b'0' "
            + "AND (pay_order_id IS NULL OR pay_order_id=#{pay}) AND pay_price>0 AND pay_deadline>#{paid} "
            + "AND auth_version=#{version} AND auth_version<9007199254740991")
    int confirmPaid(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("pay") Long pay,
            @Param("paid") LocalDateTime paid,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_log(tenant_id,order_id,actor_id,actor_type,action,from_status,to_status,order_version,create_time) "
            + "VALUES(#{tenant},#{id},0,'SYSTEM','PAY_CONFIRM',0,10,#{version},#{now})")
    int logPaid(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Select("SELECT id,tenant_id,type,user_id,status,pay_price,pay_order_id,pay_status,pay_deadline,coupon_id,cancel_type,cancel_time,cancel_resources_released,auth_version,create_time "
            + "FROM trade_order WHERE tenant_id=#{tenant} AND id=#{id}")
    TradeOrderDO paymentOrder(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} FOR UPDATE")
    TradeOrderDO lockOwned(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id);

    @Update("UPDATE trade_order SET pay_order_id=#{pay},auth_version=auth_version+1,update_time=#{now} "
            + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND status=0 AND pay_status=b'0' "
            + "AND pay_order_id IS NULL AND pay_price>0 AND pay_deadline>#{now} AND auth_version=#{version} AND auth_version<9007199254740991")
    int bindPayment(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("pay") Long pay,
            @Param("version") Long version,@Param("now") LocalDateTime now);
    @Update("UPDATE trade_order SET status=10,pay_status=b'1',pay_time=#{now},auth_version=auth_version+1,update_time=#{now} "
            + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND status=0 AND pay_status=b'0' "
            + "AND pay_order_id IS NULL AND pay_price=0 AND pay_deadline>#{now} AND auth_version=#{version} AND auth_version<9007199254740991")
    int confirmFree(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,
            @Param("version") Long version,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO trade_order_log(tenant_id,order_id,actor_id,action,from_status,to_status,order_version,create_time) "
            + "VALUES(#{tenant},#{id},#{user},#{action},0,#{to},#{version},#{now})")
    int logPayment(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("action") String action,
            @Param("to") Integer to,@Param("version") Long version,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM trade_order WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id}")
    TradeOrderDO owned(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id);

    @Insert("INSERT INTO trade_order(id,tenant_id,no,type,user_id,user_remark,status,auth_version,product_count,total_price,discount_price,"
            + "delivery_price,pay_price,coupon_id,pay_status,pay_deadline,refund_price,receiver_name,receiver_mobile,receiver_area_id,"
            + "receiver_detail_address,create_time,update_time) VALUES(#{row.id},#{row.tenantId},#{row.no},#{row.type},#{row.userId},#{row.userRemark},0,0,"
            + "#{row.productCount},#{row.totalPrice},#{row.discountPrice},#{row.deliveryPrice},#{row.payPrice},#{row.couponId},b'0',#{row.payDeadline},0,"
            + "#{row.receiverName},#{row.receiverMobile},#{row.receiverAreaId},#{row.receiverDetailAddress},#{row.createTime},#{row.updateTime})")
    int insert(@Param("row") TradeOrderDO row);

    /** 直接复制已经核价/占用的原商品项，id与库存操作中的orderItemId保持相同。 */
    @Insert("INSERT INTO trade_order_item(id,tenant_id,order_id,user_id,source_type,cart_id,spu_id,spu_name,sku_id,spu_version,sku_version,properties,pic_url,"
            + "count,price,goods_price,discount_price,delivery_price,pay_price,unit_discount_base,unit_discount_remainder,unit_delivery_base,unit_delivery_remainder) "
            + "SELECT id,tenant_id,preparation_id,#{user},source_type,cart_id,spu_id,spu_name,sku_id,spu_version,sku_version,properties,pic_url,"
            + "quantity,price,goods_price,discount_price,delivery_price,pay_price,unit_discount_base,unit_discount_remainder,unit_delivery_base,unit_delivery_remainder "
            + "FROM trade_order_prepare_item WHERE tenant_id=#{tenant} AND preparation_id=#{order}")
    int insertItems(@Param("tenant") Long tenant,@Param("user") Long user,@Param("order") Long order);

    @Insert("INSERT INTO trade_order_log(tenant_id,order_id,actor_id,action,from_status,to_status,order_version,create_time) "
            + "VALUES(#{tenant},#{order},#{user},'CREATE',NULL,0,0,#{now})")
    int logCreated(@Param("tenant") Long tenant,@Param("user") Long user,@Param("order") Long order,@Param("now") LocalDateTime now);
}
