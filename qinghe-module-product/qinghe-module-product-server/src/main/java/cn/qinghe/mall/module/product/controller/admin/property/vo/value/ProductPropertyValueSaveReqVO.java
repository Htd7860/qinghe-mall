package cn.qinghe.mall.module.product.controller.admin.property.vo.value;

import javax.validation.constraints.NotBlank;

import javax.validation.constraints.Size;

import javax.validation.constraints.Max;

import javax.validation.constraints.Min;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 商品属性值新增/更新 Request VO")
@Data
public class ProductPropertyValueSaveReqVO {

    @Schema(description = "主键", example = "1024")
    @Min(1)
    private Long id;

    @Schema(description = "属性项的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "属性项的编号不能为空")
    @Min(1)
    private Long propertyId;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "红色")
    @NotBlank(message = "属性值名称不能为空")
    @Size(max = 100)
    private String name;

    @Schema(description = "备注", example = "颜色")
    @Size(max = 512)
    private String remark;

    /** 更新必填，创建时不使用；由Service区分创建/更新校验。 */
    @Min(0)
    private Long expectedVersion;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status = 0;

}
