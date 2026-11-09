package cn.qinghe.mall.module.product.dal.dataobject.sku;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyDO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyValueDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.util.List;
import java.math.BigDecimal;

/**
 * 商品 SKU DO
 *
 * @author 芋道源码
 */
@TableName(value = "product_sku", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkuDO extends TenantBaseDo {

    /**
     * 商品 SKU 编号，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * SPU 编号
     *
     * 关联 {@link ProductSpuDO#getId()}
     */
    private Long spuId;
    /**
     * 属性数组，JSON 格式
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Property> properties;
    /**
     * 商品价格，单位：分
     */
    private Integer price;
    /**
     * 市场价，单位：分
     */
    private Integer marketPrice;
    /**
     * 成本价，单位：分
     */
    private Integer costPrice;
    /**
     * 商品条码
     */
    private String barCode;
    /**
     * 图片地址
     */
    private String picUrl;
    /**
     * 库存
     */
    private Integer stock;
    /**
     * 商品重量，单位：kg 千克
     */
    private BigDecimal weight;
    /**
     * 商品体积，单位：m^3 平米
     */
    private BigDecimal volume;


    // ========== 营销相关字段 =========

    // ========== 统计相关字段 =========
    /**
     * 商品销量
     */
    private Integer salesCount;


    /** 0启用，1停用；历史引用SKU不物理删除或挪到其他SPU。 */
    private Integer status;
    /** 规范化规格组合的SHA256；由Service生成，客户端不能随意指定。 */
    private String propertyKey;
    /** 资料版本，价格/规格修改和库存变化分别处理。 */
    private Long authVersion;

    /**
     * 商品属性
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Property {

        /**
         * 属性编号
         * 关联 {@link ProductPropertyDO#getId()}
         */
        private Long propertyId;
        /**
         * 属性名字
         * 冗余 {@link ProductPropertyDO#getName()}
         *
         * 注意：每次属性名字发生变化时，需要更新该冗余
         */
        private String propertyName;

        /**
         * 属性值编号
         * 关联 {@link ProductPropertyValueDO#getId()}
         */
        private Long valueId;
        /**
         * 属性值名字
         * 冗余 {@link ProductPropertyValueDO#getName()}
         *
         * 注意：每次属性值名字发生变化时，需要更新该冗余
         */
        private String valueName;

    }

}

