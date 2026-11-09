package cn.qinghe.mall.module.product.dal.mysql.stock;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductAfterSaleStockDO;
import org.apache.ibatis.annotations.*;

/** 写入前持原订单库存父锁、SPU/SKU锁；累计回补不能突破原扣减。 */
@Mapper
public interface ProductAfterSaleStockMapper {
    @Select("SELECT * FROM product_after_sale_stock WHERE tenant_id=#{tenant} AND after_sale_id=#{id}")
    ProductAfterSaleStockDO get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT COALESCE(SUM(quantity),0) FROM product_after_sale_stock WHERE tenant_id=#{tenant} AND operation_id=#{operation} AND order_item_id=#{item}")
    long restored(@Param("tenant") Long tenant,@Param("operation") Long operation,@Param("item") Long item);
    @Update("UPDATE product_order_stock_item SET restored_count=restored_count+#{quantity} WHERE tenant_id=#{tenant} AND operation_id=#{operation} "
        +"AND order_item_id=#{item} AND restored_count=#{before} AND restored_count+#{quantity}<=quantity AND release_before_stock IS NULL AND release_after_stock IS NULL")
    int accumulate(@Param("tenant") Long tenant,@Param("operation") Long operation,@Param("item") Long item,@Param("before") Integer before,@Param("quantity") Integer quantity);
    @Insert("INSERT INTO product_after_sale_stock(tenant_id,after_sale_id,order_id,user_id,order_item_id,operation_id,spu_id,sku_id,quantity,before_stock,after_stock,create_time) "
        +"VALUES(#{row.tenantId},#{row.afterSaleId},#{row.orderId},#{row.userId},#{row.orderItemId},#{row.operationId},#{row.spuId},#{row.skuId},#{row.quantity},#{row.beforeStock},#{row.afterStock},#{row.createTime})")
    int insert(@Param("row") ProductAfterSaleStockDO row);
}
