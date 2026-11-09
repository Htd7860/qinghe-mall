package cn.qinghe.mall.module.product.controller.admin.category.vo;

import javax.validation.constraints.Size;

import javax.validation.constraints.Max;

import javax.validation.constraints.Min;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 商品分类新增/更新 Request VO")
@Data
public class ProductCategorySaveReqVO {

    @Schema(description = "分类编号", example = "2")
    @Min(1)
    private Long id;

    @Schema(description = "父分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "父分类编号不能为空")
    @Min(0)
    private Long parentId;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "办公文具")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100)
    private String name;

    @Schema(description = "移动端分类图", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "移动端分类图不能为空")
    @Size(max = 1024)
    private String picUrl;

    @Schema(description = "分类排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Min(0)
    @Max(10000)
    private Integer sort;

    @Schema(description = "开启状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "开启状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;

    @Schema(description = "分类描述", example = "描述")
    @Size(max = 512)
    private String description;

    /** 更新必填，创建时不使用；由Service区分创建/更新校验。 */
    @Min(0)
    private Long expectedVersion;

}
