package cn.qinghe.mall.module.product.dal.mysql.catalog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 真实引用检查包含回收/逻辑删除记录，不用返回0的占位服务绕过未接业务。 */
@Mapper
public interface ProductCatalogReferenceMapper {
    @Select("SELECT COUNT(*) FROM product_spu WHERE tenant_id=#{tenantId} AND category_id=#{id}")
    Long countCategory(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM product_spu WHERE tenant_id=#{tenantId} AND brand_id=#{id}")
    Long countBrand(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM product_sku WHERE tenant_id=#{tenantId} "
            + "AND JSON_CONTAINS(properties,JSON_OBJECT('propertyId',#{id}))")
    Long countSalesProperty(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM product_spu WHERE tenant_id=#{tenantId} "
            + "AND JSON_CONTAINS(display_properties,JSON_OBJECT('propertyId',#{id}))")
    Long countDisplayProperty(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM product_sku WHERE tenant_id=#{tenantId} "
            + "AND JSON_CONTAINS(properties,JSON_OBJECT('valueId',#{id}))")
    Long countSalesValue(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM product_spu WHERE tenant_id=#{tenantId} "
            + "AND JSON_CONTAINS(display_properties,JSON_OBJECT('valueId',#{id}))")
    Long countDisplayValue(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
