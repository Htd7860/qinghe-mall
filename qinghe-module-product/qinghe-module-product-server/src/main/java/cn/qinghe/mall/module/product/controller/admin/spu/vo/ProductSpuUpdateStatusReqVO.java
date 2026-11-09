package cn.qinghe.mall.module.product.controller.admin.spu.vo;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 商品 SPU Status 更新 Request VO")
@Data
public class ProductSpuUpdateStatusReqVO{

    @Schema(description = "商品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "商品编号不能为空")
    @Min(1)
    private Long id;
    @NotNull @Min(0) private Long expectedVersion;

    @Schema(description = "商品状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "商品状态不能为空")
    @Min(-1) @Max(1)
    private Integer status;

}
