package cn.qinghe.mall.module.product.dal.dataobject.stock;

import lombok.Data;

/** 同事务保存原扣减和整单回补的实际库存前后值。 */
@Data
public class ProductOrderStockItemDO {
    private Long id;
    private Long tenantId;
    private Long operationId;
    private Long orderItemId;
    private Long spuId;
    private Long skuId;
    private Integer quantity;
    private Integer unitPrice;
    private Long spuVersion;
    private Long skuVersion;
    private Integer beforeStock;
    private Integer afterStock;
    private Integer releaseBeforeStock;
    private Integer releaseAfterStock;
    /** 售后成功回补数量，整单未付释放不能与此重复。 */
    private Integer restoredCount;
}
