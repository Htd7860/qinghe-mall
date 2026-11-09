package cn.qinghe.mall.module.product.controller.admin.property.vo.value;

import javax.validation.constraints.Min;

import javax.validation.constraints.Size;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商品属性值分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductPropertyValuePageReqVO extends PageParam {

    @Schema(description = "属性项的编号", example = "1024")
    @Min(1)
    private Long propertyId;

    @Schema(description = "名称", example = "红色")
    @Size(max = 100)
    private String name;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

}
