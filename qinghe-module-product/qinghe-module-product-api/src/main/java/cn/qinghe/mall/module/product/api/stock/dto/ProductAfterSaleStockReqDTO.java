package cn.qinghe.mall.module.product.api.stock.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 原退款计划的库存命令，仅trade专用服务构造；不接浏览器自由数量。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class ProductAfterSaleStockReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long orderId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long userId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long afterSaleId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long orderItemId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long spuId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long skuId;
    @NotNull @Min(1) @Max(9999) private Integer quantity;
}
