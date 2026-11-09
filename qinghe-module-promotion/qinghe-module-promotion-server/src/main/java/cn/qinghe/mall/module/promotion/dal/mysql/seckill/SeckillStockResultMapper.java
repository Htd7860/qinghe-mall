package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import org.apache.ibatis.annotations.*;

/** 原活动库存条件更新职责；原回执/池变化/不可变额度流水由同一父锁事务完成。 @author halfninety */
@Mapper
public interface SeckillStockResultMapper {
    @Select("SELECT * FROM promotion_seckill_stock_receipt WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND generation=#{generation} AND sku_id=#{sku} ORDER BY product_version DESC LIMIT 1")
    SeckillStockReceiptDO latest(@Param("tenant") Long tenant,@Param("activity") Long activity,
            @Param("generation") Integer generation,@Param("sku") Long sku);

    @Insert("INSERT INTO promotion_seckill_stock_receipt(tenant_id,activity_id,generation,sku_id,product_version,product_status,"
            + "allocated_quantity,returned_quantity,create_time) VALUES(#{row.tenantId},#{row.activityId},#{row.generation},#{row.skuId},"
            + "#{row.productVersion},#{row.productStatus},#{row.allocatedQuantity},#{row.returnedQuantity},UTC_TIMESTAMP(3))")
    int insert(@Param("row") SeckillStockReceiptDO row);

    @Update("UPDATE promotion_seckill_activity_sku SET allocated_quantity=#{allocated},available_quantity=#{available},"
            + "returned_quantity=#{returned},allocation_status=#{status},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND sku_id=#{sku} AND version=#{expected} "
            + "AND version<9007199254740991 AND held_quantity=0 AND sold_quantity=0 AND return_pending_quantity=0 "
            + "AND allocation_operation_no=#{operation} AND allocation_status IN(10,20,40)")
    int apply(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("sku") Long sku,@Param("expected") Long expected,
            @Param("operation") String operation,@Param("allocated") Integer allocated,@Param("available") Integer available,
            @Param("returned") Integer returned,@Param("status") Integer status);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,event_type,allocated_delta,"
            + "available_delta,returned_delta,source_event_id,create_time) "
            + "VALUES(#{tenant},#{operation},#{activity},#{generation},#{sku},#{type},#{allocated},#{available},#{returned},#{source},UTC_TIMESTAMP(3))")
    int journal(@Param("tenant") Long tenant,@Param("operation") String operation,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("sku") Long sku,
            @Param("type") String type,@Param("allocated") Integer allocated,@Param("available") Integer available,
            @Param("returned") Integer returned,@Param("source") String source);

    @Update("UPDATE promotion_seckill_activity SET status=60,version=version+1,stop_reason=#{reason},stopped_by=#{actor},"
            + "updater=#{actor},update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND id=#{id} AND generation=#{generation} "
            + "AND publish_operation_no=#{request} AND status=10 AND version=#{expected} AND deleted=b'0' AND version<9007199254740991")
    int fail(@Param("tenant") Long tenant,@Param("id") Long id,@Param("generation") Integer generation,@Param("request") String request,
            @Param("expected") Long expected,@Param("actor") String actor,@Param("reason") String reason);
}
