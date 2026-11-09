package cn.qinghe.mall.module.product.dal.dataobject.spu;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.qinghe.mall.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.enums.spu.ProductSpuStatusEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.util.List;

/**
 * 商品 SPU DO
 *
 * @author 芋道源码
 */
@TableName(value = "product_spu", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpuDO extends TenantBaseDo {

    /**
     * 商品 SPU 编号，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    // ========== 基本信息 =========

    /**
     * 商品名称
     */
    private String name;
    /**
     * 关键字
     */
    private String keyword;
    /**
     * 商品简介
     */
    private String introduction;
    /**
     * 商品详情
     */
    private String description;

    /**
     * 商品分类编号
     *
     * 关联 {@link ProductCategoryDO#getId()}
     */
    private Long categoryId;
    /**
     * 商品品牌编号
     *
     * 关联 {@link ProductBrandDO#getId()}
     */
    private Long brandId;
    /**
     * 商品封面图
     */
    private String picUrl;
    /**
     * 商品轮播图
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sliderPicUrls;

    /**
     * 排序字段
     */
    private Integer sort;
    /**
     * 商品状态
     *
     * 枚举 {@link ProductSpuStatusEnum}
     */
    private Integer status;

    // ========== SKU 相关字段 =========

    /**
     * 规格类型
     *
     * false - 单规格
     * true - 多规格
     */
    private Boolean specType;
    /**
     * 商品价格，单位使用：分
     *
     * 基于其对应的 {@link ProductSkuDO#getPrice()} sku单价最低的商品的
     */
    private Integer price;
    /**
     * 市场价，单位使用：分
     *
     * 基于其对应的 {@link ProductSkuDO#getMarketPrice()} sku单价最低的商品的
     */
    private Integer marketPrice;
    /**
     * 成本价，单位使用：分
     *
     * 基于其对应的 {@link ProductSkuDO#getCostPrice()} sku单价最低的商品的
     */
    private Integer costPrice;
    /**
     * 库存
     *
     * 基于其对应的 {@link ProductSkuDO#getStock()} 求和
     */
    private Integer stock;

    // ========== 物流相关字段 =========

    /**
     * 物流配置模板编号
     *
     * 对应 TradeDeliveryExpressTemplateDO 的 id 编号
     */
    private Long deliveryTemplateId;

    // ========== 统计相关字段 =========

    /**
     * 商品销量
     */
    private Integer salesCount;
    /**
     * 浏览量
     */
    private Integer browseCount;

    /** 后台资料更新版本；库存接口不接收旧表单的库存绝对值。 */
    private Long authVersion;
    /** 当前经营资料归属，用于后续部门/本人数据范围。 */
    private Long ownerUserId;
    private Long ownerDeptId;
    /** 展示属性快照，不参与SKU组合；每项仍关联真实属性/值。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ProductSkuDO.Property> displayProperties;

}
