package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

/** 择取原 SeckillActivityCreate/UpdateReqVO；不接受库存余额、状态或展示快照。 */
@Data
public class SeckillActivitySaveReqVO {
    @Min(1) @Max(9007199254740991L)
    private Long id;
    @Min(0) @Max(9007199254740991L)
    private Long expectedVersion;
    @NotBlank @Size(max=60)
    private String name;
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long productSpuId;
    @NotNull
    private OffsetDateTime startTime;
    @NotNull
    private OffsetDateTime endTime;
    @NotNull @Min(1) @Max(9999)
    private Integer singleLimit;
    @NotNull @Min(1) @Max(2147483647L)
    private Integer memberLimit;
    @NotEmpty @Size(max=200) @Valid
    private List<Sku> skus;

    @Data
    public static class Sku {
        @NotNull @Min(1) @Max(9007199254740991L)
        private Long skuId;
        @NotNull @Min(1) @Max(2000000000)
        private Integer priceCent;
        @NotNull @Min(1) @Max(2147483647L)
        private Integer plannedQuantity;
    }
}
