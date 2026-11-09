package cn.qinghe.mall.module.product.dal.mysql.seckill;

import cn.qinghe.mall.module.product.dal.dataobject.seckill.ProductSeckillAllocationDO;
import org.apache.ibatis.annotations.*;

/**
 * 择取 yudao ProductSkuMapper.updateStockDecr 的 stock >= count 条件更新。
 * 本项目增加发布凭证和同事务流水；先锁原业务凭证，再锁 SPU、SKU，所有语句绑定 tenant。
 * @author 芋道源码
 */
@Mapper
public interface ProductSeckillAllocationMapper {
    // 不使用 INSERT IGNORE 吞掉字段/约束错误；重复键仅触碰原记录，随后必须核对原业务键及摘要。
    @Insert("INSERT INTO product_seckill_allocation(tenant_id,activity_no,generation,spu_id,sku_id,spu_version,sku_version,"
            + "operation_no,payload_hash,quantity,allocated_quantity,returned_quantity,status,version,create_time,update_time) "
            + "VALUES(#{row.tenantId},#{row.activityNo},#{row.generation},#{row.spuId},#{row.skuId},#{row.spuVersion},#{row.skuVersion},"
            + "#{row.operationNo},#{row.payloadHash},#{row.quantity},0,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) "
            + "ON DUPLICATE KEY UPDATE id=id")
    int ensure(@Param("row") ProductSeckillAllocationDO row);

    @Select("SELECT * FROM product_seckill_allocation WHERE tenant_id=#{tenant} AND operation_no=#{operation} FOR UPDATE")
    ProductSeckillAllocationDO lock(@Param("tenant") Long tenant,@Param("operation") String operation);

    @Select("SELECT * FROM product_seckill_allocation WHERE tenant_id=#{tenant} AND operation_no=#{operation}")
    ProductSeckillAllocationDO get(@Param("tenant") Long tenant,@Param("operation") String operation);

    @Update("UPDATE product_sku SET stock=stock-#{row.quantity},updater=#{actor},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{row.tenantId} AND spu_id=#{row.spuId} AND id=#{row.skuId} "
            + "AND status=0 AND deleted=b'0' AND auth_version=#{row.skuVersion} AND stock>=#{row.quantity}")
    int deduct(@Param("row") ProductSeckillAllocationDO row,@Param("actor") String actor);

    @Update("UPDATE product_seckill_allocation SET allocated_quantity=quantity,status=10,version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{row.tenantId} AND id=#{row.id} AND version=#{row.version} AND status=0 "
            + "AND allocated_quantity=0 AND returned_quantity=0")
    int allocated(@Param("row") ProductSeckillAllocationDO row);

    @Update("UPDATE product_seckill_allocation SET returned_quantity=allocated_quantity,status=20,version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{row.tenantId} AND id=#{row.id} AND version=#{row.version} "
            + "AND status IN(0,10) AND returned_quantity=0")
    int compensated(@Param("row") ProductSeckillAllocationDO row);

    @Update("UPDATE product_seckill_allocation SET returned_quantity=#{target},status=CASE WHEN #{target}=allocated_quantity THEN 30 ELSE 50 END,"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{row.tenantId} AND id=#{row.id} AND version=#{row.version} "
            + "AND version<9007199254740991 AND status IN(10,50) AND allocated_quantity=quantity AND returned_quantity=#{row.returnedQuantity} "
            + "AND #{target}>returned_quantity AND #{target}<=allocated_quantity")
    int returned(@Param("row") ProductSeckillAllocationDO row,@Param("target") Integer cumulativeTarget);

    @Insert("INSERT INTO product_seckill_stock_entry(tenant_id,allocation_id,sku_id,operation_no,payload_hash,event_type,quantity,"
            + "before_stock,after_stock,reason,create_time) VALUES(#{row.tenantId},#{row.id},#{row.skuId},"
            + "CONCAT(#{row.operationNo},':RETURN:',#{target}),#{row.payloadHash},'RETURN',#{delta},#{before},#{after},'CLOSED_ACTIVITY_RETURN',UTC_TIMESTAMP(3))")
    int journalReturn(@Param("row") ProductSeckillAllocationDO row,@Param("target") Integer cumulativeTarget,
            @Param("delta") Integer delta,@Param("before") Integer beforeStock,@Param("after") Integer afterStock);

    @Insert("INSERT INTO product_seckill_stock_entry(tenant_id,allocation_id,sku_id,operation_no,payload_hash,event_type,quantity,"
            + "before_stock,after_stock,reason,create_time) VALUES(#{row.tenantId},#{row.id},#{row.skuId},#{operation},#{row.payloadHash},"
            + "#{type},#{row.quantity},#{before},#{after},#{reason},UTC_TIMESTAMP(3))")
    int journal(@Param("row") ProductSeckillAllocationDO row,@Param("operation") String operation,
            @Param("type") String type,@Param("before") Integer before,@Param("after") Integer after,@Param("reason") String reason);
}
