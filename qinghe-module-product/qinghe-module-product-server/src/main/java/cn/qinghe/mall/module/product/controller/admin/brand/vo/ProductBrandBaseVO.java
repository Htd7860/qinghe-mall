package cn.qinghe.mall.module.product.controller.admin.brand.vo;

import javax.validation.constraints.NotBlank;

import javax.validation.constraints.Size;

import javax.validation.constraints.Max;

import javax.validation.constraints.Min;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
* 商品品牌 Base VO，提供给添加、修改、详细的子 VO 使用
* 如果子 VO 存在差异的字段，请不要添加到这里，影响 Swagger 文档生成
*/
@Data
public class ProductBrandBaseVO {

    @Schema(description = "品牌名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "苹果")
    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 100)
    private String name;

    @Schema(description = "品牌图片", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "品牌图片不能为空")
    @Size(max = 1024)
    private String picUrl;

    @Schema(description = "品牌排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "品牌排序不能为空")
    @Min(0)
    @Max(10000)
    private Integer sort;

    @Schema(description = "品牌描述", example = "描述")
    @Size(max = 512)
    private String description;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;

}
