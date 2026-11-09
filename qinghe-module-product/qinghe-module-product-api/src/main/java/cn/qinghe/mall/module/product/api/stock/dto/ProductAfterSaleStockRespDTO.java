package cn.qinghe.mall.module.product.api.stock.dto;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 已提交库存回补的原回执，重复读取不随SKU当前库存变化。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class ProductAfterSaleStockRespDTO {
    private Long orderId;
    private Long userId;
    private Long afterSaleId;
    private Long orderItemId;
    private Long spuId;
    private Long skuId;
    private Integer quantity;
    private Integer beforeStock;
    private Integer afterStock;
    private LocalDateTime restoredTime;
}
