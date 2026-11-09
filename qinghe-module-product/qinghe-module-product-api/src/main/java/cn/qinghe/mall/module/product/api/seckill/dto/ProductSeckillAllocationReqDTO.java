package cn.qinghe.mall.module.product.api.seckill.dto;

import lombok.Data;
import javax.validation.constraints.*;

/**
 * promotion 从已冻结的发布快照构造；同一发布代次、SKU 始终使用同一业务键。
 * 不接收租户/员工身份，不是消费者下单或后台手工增减库存的请求。
 */
@Data
public class ProductSeckillAllocationReqDTO {
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9:_-]{0,63}")
    private String activityNo;
    @NotNull @Min(1)
    private Integer generation;
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9:_-]{0,63}")
    private String operationNo;
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long spuId;
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long skuId;
    @NotNull @Min(0) @Max(9007199254740991L)
    private Long spuVersion;
    @NotNull @Min(0) @Max(9007199254740991L)
    private Long skuVersion;
    @NotNull @Min(1) @Max(2147483647L)
    private Integer quantity;
}
