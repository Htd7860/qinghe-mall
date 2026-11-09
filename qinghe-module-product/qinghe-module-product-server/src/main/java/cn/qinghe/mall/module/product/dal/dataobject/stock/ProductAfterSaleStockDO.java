package cn.qinghe.mall.module.product.dal.dataobject.stock;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 原售后号的已提交回补流水，复用原库存增量/前后值职责。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class ProductAfterSaleStockDO {
    private Long tenantId;
    private Long afterSaleId;
    private Long orderId;
    private Long userId;
    private Long orderItemId;
    private Long operationId;
    private Long spuId;
    private Long skuId;
    private Integer quantity;
    private Integer beforeStock;
    private Integer afterStock;
    private LocalDateTime createTime;
}
