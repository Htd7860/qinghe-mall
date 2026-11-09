package cn.qinghe.mall.module.product.api.seckill.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;

/** promotion原清算计划构造：原划拨元组和累计归还目标，不能当作自由增库存命令。 */
@Data
public class ProductSeckillReturnReqDTO {
    @NotNull @Valid
    private ProductSeckillAllocationReqDTO allocation;
    @NotNull @Min(1) @Max(2147483647L)
    private Integer returnedQuantity;
}
