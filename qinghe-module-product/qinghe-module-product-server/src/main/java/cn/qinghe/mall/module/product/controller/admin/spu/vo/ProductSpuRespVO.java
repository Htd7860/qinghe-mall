package cn.qinghe.mall.module.product.controller.admin.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 商品 SPU Response VO")
@Data

public class ProductSpuRespVO {

    @Schema(description = "商品 SPU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "111")

    private Long id;

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "清凉小短袖")

    private String name;

    @Schema(description = "关键字", requiredMode = Schema.RequiredMode.REQUIRED, example = "清凉丝滑不出汗")

    private String keyword;

    @Schema(description = "商品简介", requiredMode = Schema.RequiredMode.REQUIRED, example = "清凉小短袖简介")

    private String introduction;

    @Schema(description = "商品详情", requiredMode = Schema.RequiredMode.REQUIRED, example = "清凉小短袖详情")

    private String description;

    @Schema(description = "商品分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")

    private Long categoryId;

    @Schema(description = "商品品牌编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")

    private Long brandId;

    @Schema(description = "商品封面图", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/xx.png")

    private String picUrl;

    @Schema(description = "商品轮播图", requiredMode = Schema.RequiredMode.REQUIRED, example = "[https://www.iocoder.cn/xx.png, https://www.iocoder.cn/xxx.png]")
    private List<String> sliderPicUrls;

    @Schema(description = "排序字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")

    private Integer sort;

    @Schema(description = "商品状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")


    private Integer status;

    @Schema(description = "商品创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2023-05-24 00:00:00")

    private LocalDateTime createTime;

    // ========== SKU 相关字段 =========

    @Schema(description = "规格类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")

    private Boolean specType;

    @Schema(description = "商品价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "1999")

    private Integer price;

    @Schema(description = "市场价，单位使用：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "199")

    private Integer marketPrice;

    @Schema(description = "成本价，单位使用：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "19")

    private Integer costPrice;

    @Schema(description = "商品库存", requiredMode = Schema.RequiredMode.REQUIRED, example = "10000")

    private Integer stock;

    @Schema(description = "SKU 数组")
    private List<ProductSkuRespVO> skus;

    // ========== 物流相关字段 =========


    @Schema(description = "物流配置模板编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "111")

    private Long deliveryTemplateId;

    // ========== 营销相关字段 =========



    // ========== 统计相关字段 =========

    @Schema(description = "商品销量", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")

    private Integer salesCount;


    @Schema(description = "浏览量", requiredMode = Schema.RequiredMode.REQUIRED, example = "888")

    private Integer browseCount;

    private Long authVersion;
    private Long ownerUserId;
    private Long ownerDeptId;
    private List<ProductSkuSaveReqVO.Property> displayProperties;
}
