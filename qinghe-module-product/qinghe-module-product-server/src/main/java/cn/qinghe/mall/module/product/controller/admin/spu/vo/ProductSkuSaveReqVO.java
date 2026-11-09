package cn.qinghe.mall.module.product.controller.admin.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** 取自yudao同名VO：去掉分销和未使用SKU名字，增加稳定id/资料版本、金额与计量边界。 */
@Schema(description = "管理后台 - 商品 SKU 创建/更新 Request VO")
@Data
public class ProductSkuSaveReqVO {
    @Min(1) private Long id;
    /** 更新已有SKU必填；新SKU由Service置0，不由该字段指定版本。 */
    @Min(0) private Long expectedVersion;
    @NotNull @Min(1) private Integer price;
    @Min(0) private Integer marketPrice;
    @Min(0) private Integer costPrice;
    @Size(max = 100) private String barCode;
    @NotBlank @Size(max = 1024) private String picUrl;
    /** 仅新SKU的初始库存；编辑已有SKU必须省略，后续库存调整走独立流水。 */
    @Min(0) private Integer stock;
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6)
    private BigDecimal weight;
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6)
    private BigDecimal volume;
    @NotNull @Min(0) @Max(1) private Integer status = 0;
    @Valid @Size(max = 20) private List<@NotNull Property> properties;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Property {
        // 0仅用于单规格的默认属性，多规格Service明确要求正数且查真实记录。
        @NotNull @Min(0) private Long propertyId;
        /** 只作兼容回显；保存时由服务器真实属性名称覆盖。 */
        @Size(max = 100) private String propertyName;
        @NotNull @Min(0) private Long valueId;
        @Size(max = 100) private String valueName;
    }
}
