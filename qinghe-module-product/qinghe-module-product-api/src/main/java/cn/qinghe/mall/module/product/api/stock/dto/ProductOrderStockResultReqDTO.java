package cn.qinghe.mall.module.product.api.stock.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class ProductOrderStockResultReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long orderId;
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long userId;
}
