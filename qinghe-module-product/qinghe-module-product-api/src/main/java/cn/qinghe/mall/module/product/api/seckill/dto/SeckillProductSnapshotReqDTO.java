package cn.qinghe.mall.module.product.api.seckill.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.util.List;

/** 仅指定本次活动选中的商品/规格，租户和展示名称不能由调用方指定。 */
@Data
public class SeckillProductSnapshotReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long spuId;
    @NotEmpty @Size(max=200)
    private List<@NotNull @Min(1) @Max(9007199254740991L) Long> skuIds;
}
