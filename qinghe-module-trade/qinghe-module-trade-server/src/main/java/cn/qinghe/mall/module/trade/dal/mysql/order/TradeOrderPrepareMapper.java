package cn.qinghe.mall.module.trade.dal.mysql.order;

import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareItemDO;
import org.apache.ibatis.annotations.*;

/** 原 TradeOrderMapper/ItemMapper 保存职责；准备阶段不写可付款订单。 */
@Mapper
public interface TradeOrderPrepareMapper {
    @Select("SELECT id,tenant_id,user_id,stage,source_type FROM trade_order_prepare WHERE tenant_id=#{tenant} AND id=#{id}")
    TradeOrderPrepareDO progress(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE trade_order_prepare SET stage=#{to},auth_version=auth_version+1,updated_at=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenant} AND id=#{id} AND stage=#{from} AND auth_version=#{version} AND auth_version<9007199254740991 "
            + "AND ((#{from}='PREPARED' AND #{to}='PROCESSING') OR (#{from}='PROCESSING' AND #{to}='CREATED'))")
    int createStage(@Param("tenant") Long tenant,@Param("id") Long id,@Param("from") String from,
            @Param("to") String to,@Param("version") Long version);

    /** 与最终正式建单共用的串行化行；后续CREATED必须在持有该锁的同一事务写主订单。 */
    @Select("SELECT id,tenant_id,user_id,request_id,quote_id,no,stage,auth_version,prepare_deadline,source_type FROM trade_order_prepare "
            + "WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    TradeOrderPrepareDO lockProgress(@Param("tenant") Long tenant,@Param("id") Long id);

    @Update("UPDATE trade_order_prepare SET stage=#{to},auth_version=auth_version+1,updated_at=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenant} AND id=#{id} AND stage=#{from} AND auth_version=#{version} AND auth_version<9007199254740991 "
            + "AND ((#{from} IN('PREPARED','PROCESSING') AND #{to}='COMPENSATING') OR (#{from}='COMPENSATING' AND #{to}='FAILED'))")
    int compensateStage(@Param("tenant") Long tenant,@Param("id") Long id,@Param("from") String from,
            @Param("to") String to,@Param("version") Long version);

    @Select("SELECT * FROM trade_order_prepare WHERE tenant_id=#{tenant} AND id=#{id}")
    TradeOrderPrepareDO get(@Param("tenant") Long tenant,@Param("id") Long id);

    @Select("SELECT id,tenant_id,preparation_id,cart_id,cart_line_version,spu_id,spu_name,sku_id,spu_version,sku_version,"
            + "quantity AS count,price,goods_price,discount_price,delivery_price,pay_price,"
            + "unit_discount_base,unit_discount_remainder,unit_delivery_base,unit_delivery_remainder "
            + "FROM trade_order_prepare_item WHERE tenant_id=#{tenant} AND preparation_id=#{id} ORDER BY cart_id LIMIT 201")
    java.util.List<TradeOrderPrepareItemDO> items(@Param("tenant") Long tenant,@Param("id") Long id);

    @Select("SELECT id,tenant_id,user_id,request_id,request_hash,quote_id,no,stage FROM trade_order_prepare "
            + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND source_type=0 AND request_id=#{request}")
    TradeOrderPrepareDO find(@Param("tenant") Long tenant,@Param("user") Long user,@Param("request") String request);

    @Insert("INSERT INTO trade_order_prepare(tenant_id,user_id,request_id,request_hash,quote_id,no,stage,auth_version,remark,snapshot,"
            + "product_count,total_price,discount_price,delivery_price,pay_price,prepare_deadline,created_at,updated_at) "
            + "VALUES(#{row.tenantId},#{row.userId},#{row.requestId},#{row.requestHash},#{row.quoteId},#{row.no},'PREPARED',0,#{row.remark},CAST(#{row.snapshot} AS JSON),"
            + "#{row.productCount},#{row.totalPrice},#{row.discountPrice},#{row.deliveryPrice},#{row.payPrice},#{row.prepareDeadline},#{row.createdAt},#{row.updatedAt})")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insert(@Param("row") TradeOrderPrepareDO row);

    @Insert("INSERT INTO trade_order_prepare_item(tenant_id,preparation_id,source_type,cart_id,cart_line_version,spu_id,spu_name,sku_id,spu_version,sku_version,properties,pic_url,"
            + "quantity,price,goods_price,discount_price,delivery_price,pay_price,unit_discount_base,unit_discount_remainder,unit_delivery_base,unit_delivery_remainder) "
            + "VALUES(#{row.tenantId},#{row.preparationId},COALESCE(#{row.sourceType},0),#{row.cartId},#{row.cartLineVersion},#{row.spuId},#{row.spuName},#{row.skuId},#{row.spuVersion},#{row.skuVersion},"
            + "CAST(#{row.properties} AS JSON),#{row.picUrl},#{row.count},#{row.price},#{row.goodsPrice},#{row.discountPrice},#{row.deliveryPrice},#{row.payPrice},"
            + "#{row.unitDiscountBase},#{row.unitDiscountRemainder},#{row.unitDeliveryBase},#{row.unitDeliveryRemainder})")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insertItem(@Param("row") TradeOrderPrepareItemDO row);

    @Insert("INSERT INTO trade_order_outbox(event_id,tenant_id,preparation_id,event_type,aggregate_version,schema_version,payload,status,attempts,created_at,available_at) "
            + "VALUES(#{event},#{tenant},#{preparation},'ORDER_PREPARED',0,1,CAST(#{payload} AS JSON),'PENDING',0,#{now},#{now})")
    int insertPreparedEvent(@Param("event") String event,@Param("tenant") Long tenant,@Param("preparation") Long preparation,
            @Param("payload") String payload,@Param("now") java.time.LocalDateTime now);
}
