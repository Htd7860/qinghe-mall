package cn.qinghe.mall.module.product.controller.admin.spu.vo;
import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
/** 手工库存输入只接受增减量，业务键跨重试保持不变。 */
@Data
public class ProductStockAdjustReqVO {
    @NotNull @Min(1) private Long spuId;
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9:_-]{7,79}") private String operationKey;
    @NotBlank @Size(max=512) private String reason;
    @Valid @NotEmpty @Size(max=2000) private List<@NotNull Item> items;
    @Data
    public static class Item {
        @NotNull @Min(1) private Long skuId;
        @NotNull @Min(-2147483647L) private Integer delta;
    }
}
