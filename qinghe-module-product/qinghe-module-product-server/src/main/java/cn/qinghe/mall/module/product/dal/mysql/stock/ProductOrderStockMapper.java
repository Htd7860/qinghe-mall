package cn.qinghe.mall.module.product.dal.mysql.stock;

import cn.qinghe.mall.module.product.api.stock.dto.ProductOrderStockReqDTO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductOrderStockDO;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductOrderStockItemDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 原 ProductSkuMapper 的条件减库存/加库存写法，改为参数绑定和固定租户。
 * 所有写方法只由持有订单结果行、SPU 父行及 SKU 行锁的 Service 调用。
 *
 * @author 芋道源码
 */
@Mapper
public interface ProductOrderStockMapper {
    /** Preserve the existing fresh=1/duplicate=0 contract independently of CLIENT_FOUND_ROWS.
     * The no-op duplicate branch must not use LAST_INSERT_ID(id): only a fresh insert returns a generated key. */
    default int ensure(ProductOrderStockDO row) {
        row.setId(null);
        int affected=insertOrLock(row);
        if(affected!=0 && affected!=1)throw new IllegalStateException("订单库存登记影响行数异常");
        return row.getId()!=null && row.getId()>0?1:0;
    }
    @Insert("INSERT INTO product_order_stock(tenant_id,order_id,user_id,request_hash,request_snapshot,status,release_reason) "
            + "VALUES(#{row.tenantId},#{row.orderId},#{row.userId},#{row.requestHash},CAST(#{row.requestSnapshot} AS JSON),#{row.status},#{row.releaseReason}) ON DUPLICATE KEY UPDATE id=id")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insertOrLock(@Param("row") ProductOrderStockDO row);

    @Select("SELECT * FROM product_order_stock WHERE tenant_id=#{tenant} AND order_id=#{order} FOR UPDATE")
    ProductOrderStockDO lock(@Param("tenant") Long tenant,@Param("order") Long order);

    @Select("SELECT * FROM product_order_stock WHERE tenant_id=#{tenant} AND order_id=#{order}")
    ProductOrderStockDO get(@Param("tenant") Long tenant,@Param("order") Long order);

    @Select("SELECT * FROM product_order_stock_item WHERE tenant_id=#{tenant} AND operation_id=#{operation} ORDER BY order_item_id LIMIT 201")
    List<ProductOrderStockItemDO> items(@Param("tenant") Long tenant,@Param("operation") Long operation);

    // 恢复不受上下架、逻辑删除或员工数据范围阻止；新扣减的可售检查在 Service 完成。
    @Select("SELECT id,tenant_id,status,auth_version,deleted FROM product_spu WHERE tenant_id=#{tenant} AND id=#{spu} FOR UPDATE")
    ProductSpuDO lockSpu(@Param("tenant") Long tenant,@Param("spu") Long spu);

    @Select("SELECT id,tenant_id,spu_id,status,price,stock,auth_version,deleted FROM product_sku "
            + "WHERE tenant_id=#{tenant} AND spu_id=#{spu} AND id=#{sku} FOR UPDATE")
    ProductSkuDO lockSku(@Param("tenant") Long tenant,@Param("spu") Long spu,@Param("sku") Long sku);

    @Update("UPDATE product_sku SET stock=stock-#{item.quantity},updater=#{actor},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenant} AND spu_id=#{item.spuId} AND id=#{item.skuId} AND status=0 AND deleted=b'0' "
            + "AND price=#{item.unitPrice} AND auth_version=#{item.skuVersion} AND stock>=#{item.quantity}")
    int deduct(@Param("tenant") Long tenant,@Param("item") ProductOrderStockReqDTO.Item item,@Param("actor") String actor);

    @Update("UPDATE product_sku SET stock=stock+#{count},updater=#{actor},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenant} AND spu_id=#{spu} AND id=#{sku} AND stock<=2147483647-#{count}")
    int restore(@Param("tenant") Long tenant,@Param("spu") Long spu,@Param("sku") Long sku,
            @Param("count") Integer count,@Param("actor") String actor);

    @Update("UPDATE product_spu SET stock=#{stock},updater=#{actor},update_time=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{tenant} AND id=#{spu}")
    int summarize(@Param("tenant") Long tenant,@Param("spu") Long spu,@Param("stock") Integer stock,@Param("actor") String actor);

    @Insert("INSERT INTO product_order_stock_item(tenant_id,operation_id,order_item_id,spu_id,sku_id,quantity,unit_price,spu_version,sku_version,before_stock,after_stock) "
            + "VALUES(#{row.tenantId},#{row.operationId},#{row.orderItemId},#{row.spuId},#{row.skuId},#{row.quantity},#{row.unitPrice},#{row.spuVersion},#{row.skuVersion},#{row.beforeStock},#{row.afterStock})")
    int insertItem(@Param("row") ProductOrderStockItemDO row);

    @Update("UPDATE product_order_stock_item SET release_before_stock=#{before},release_after_stock=#{after} "
            + "WHERE tenant_id=#{tenant} AND operation_id=#{operation} AND id=#{id} AND restored_count=0 AND release_before_stock IS NULL AND release_after_stock IS NULL")
    int releaseItem(@Param("tenant") Long tenant,@Param("operation") Long operation,@Param("id") Long id,
            @Param("before") Integer before,@Param("after") Integer after);

    @Update("UPDATE product_order_stock SET status=2,release_reason=#{reason},updated_at=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenant} AND id=#{id} AND status=1")
    int release(@Param("tenant") Long tenant,@Param("id") Long id,@Param("reason") String reason);
}
