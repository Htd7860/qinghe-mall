package cn.qinghe.mall.module.product.controller.admin.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO.Property;

/** 取自yudao同名VO；保留商品/SKU结构，移除范围外积分、分销和外部可改统计字段。 */
@Schema(description = "管理后台 - 商品 SPU 新增/更新 Request VO")
@Data
public class ProductSpuSaveReqVO {
    @Min(1) private Long id;
    @Min(0) private Long expectedVersion;
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 255) private String keyword;
    @Size(max = 512) private String introduction;
    /** 后续输出以文本展示或经白名单净化，不可直接信任为可执行HTML。 */
    @NotBlank @Size(max = 200000) private String description;
    @NotNull @Min(1) private Long categoryId;
    @NotNull @Min(1) private Long brandId;
    @NotBlank @Size(max = 1024) private String picUrl;
    @NotEmpty @Size(max = 20)
    private List<@NotBlank @Size(max = 1024) String> sliderPicUrls;
    @NotNull @Min(0) @Max(10000) private Integer sort;
    @NotNull private Boolean specType;
    @NotNull @Min(1) private Long deliveryTemplateId;
    @Valid @Size(max = 100)
    private List<@NotNull Property> displayProperties;
    @Valid @NotEmpty @Size(max = 2000)
    private List<@NotNull ProductSkuSaveReqVO> skus;
}
