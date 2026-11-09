package cn.qinghe.mall.module.product.dal.mysql.stock;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductStockOperationDO;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductStockRecordDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** 每条SQL显式tenant，完整事务先锁SPU，再按SKU id排序锁行。 */
@Mapper
public interface ProductStockMapper {
    /** Only a newly generated key denotes first execution; duplicate no-op takes the write lock. */
    default int ensureOperation(ProductStockOperationDO row) {
        row.setId(null);
        int affected=insertOrLockOperation(row);
        if(affected!=0 && affected!=1)throw new IllegalStateException("库存操作登记影响行数异常");
        return row.getId()!=null && row.getId()>0?1:0;
    }
    @Insert("INSERT INTO product_stock_operation(tenant_id,operation_key,kind,spu_id,request_hash,actor_user_id,reason) "
            + "VALUES(#{row.tenantId},#{row.operationKey},#{row.kind},#{row.spuId},#{row.requestHash},#{row.actorUserId},#{row.reason}) ON DUPLICATE KEY UPDATE id=id")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insertOrLockOperation(@Param("row") ProductStockOperationDO row);
    @Select("SELECT * FROM product_stock_operation WHERE tenant_id=#{tenantId} AND operation_key=#{key} FOR UPDATE")
    ProductStockOperationDO lockOperation(@Param("tenantId") Long tenantId,@Param("key") String key);
    @Select("SELECT * FROM product_stock_operation WHERE tenant_id=#{tenantId} AND operation_key=#{key}")
    ProductStockOperationDO getOperation(@Param("tenantId") Long tenantId,@Param("key") String key);
    @Insert("INSERT INTO product_stock_record(tenant_id,operation_id,spu_id,sku_id,kind,delta,before_stock,after_stock) "
            + "VALUES(#{row.tenantId},#{row.operationId},#{row.spuId},#{row.skuId},#{row.kind},#{row.delta},#{row.beforeStock},#{row.afterStock})")
    int insertRecord(@Param("row") ProductStockRecordDO row);
    @Select("SELECT id,tenant_id,operation_id,spu_id,sku_id,kind,delta,before_stock,after_stock,create_time "
            + "FROM product_stock_record WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} ORDER BY sku_id")
    List<ProductStockRecordDO> getRecords(@Param("tenantId") Long tenantId,@Param("operationId") Long operationId);
    @Update("UPDATE product_sku SET stock=stock+#{delta},updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND spu_id=#{spuId} AND id=#{skuId} AND deleted=b'0' "
            + "AND stock+#{delta} BETWEEN 0 AND 2147483647")
    int changeStock(@Param("tenantId") Long tenantId,@Param("spuId") Long spuId,@Param("skuId") Long skuId,
                    @Param("delta") Integer delta,@Param("actorId") String actorId);
    @Update("UPDATE product_spu SET stock=#{stock},updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{spuId} AND deleted=b'0'")
    int updateSpuStock(@Param("tenantId") Long tenantId,@Param("spuId") Long spuId,
                       @Param("stock") Integer stock,@Param("actorId") String actorId);
}
