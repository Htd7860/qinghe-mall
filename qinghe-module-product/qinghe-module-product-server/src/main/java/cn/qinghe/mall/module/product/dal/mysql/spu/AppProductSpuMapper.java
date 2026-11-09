package cn.qinghe.mall.module.product.dal.mysql.spu;
import cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductCatalogRespVO;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 择取原App分页/固定排序与启用SKU查询职责；所有连接显式限定同店铺，无SQL旁路。 @author 芋道源码 */
@Mapper
public interface AppProductSpuMapper {
    String SKU_VISIBLE="EXISTS(SELECT 1 FROM product_sku k WHERE k.tenant_id=p.tenant_id AND k.spu_id=p.id AND k.deleted=b'0' AND k.status=0)";
    String FROM=" FROM product_spu p JOIN product_brand b ON b.tenant_id=p.tenant_id AND b.id=p.brand_id AND b.deleted=b'0' "
            +"JOIN product_category c ON c.tenant_id=p.tenant_id AND c.id=p.category_id AND c.deleted=b'0' "
            +"LEFT JOIN product_category c2 ON c2.tenant_id=p.tenant_id AND c2.id=c.parent_id AND c2.deleted=b'0' "
            +"LEFT JOIN product_category c3 ON c3.tenant_id=p.tenant_id AND c3.id=c2.parent_id AND c3.deleted=b'0' "
            +"WHERE p.tenant_id=#{tenant} AND p.deleted=b'0' AND p.status=1 AND "+SKU_VISIBLE
            +" AND (c.parent_id=0 OR c2.parent_id=0 OR c3.parent_id=0)";
    String FIELDS="SELECT p.tenant_id,p.id,p.name,p.introduction,p.category_id,c.name AS category_name,p.brand_id,b.name AS brand_name,"
            +"p.pic_url,p.spec_type,p.price,p.market_price,p.stock,p.sales_count";
    String FILTER=" AND (#{q.keyword} IS NULL OR p.name LIKE #{q.keyword} ESCAPE '!' OR p.keyword LIKE #{q.keyword} ESCAPE '!' "
            +"OR b.name LIKE #{q.keyword} ESCAPE '!' OR c.name LIKE #{q.keyword} ESCAPE '!' OR c2.name LIKE #{q.keyword} ESCAPE '!' OR c3.name LIKE #{q.keyword} ESCAPE '!') "
            +"AND (#{q.categoryId} IS NULL OR c.id=#{q.categoryId} OR c2.id=#{q.categoryId} OR c3.id=#{q.categoryId}) "
            +"AND (#{q.brandId} IS NULL OR p.brand_id=#{q.brandId}) AND (#{q.minPrice} IS NULL OR p.price>=#{q.minPrice}) "
            +"AND (#{q.maxPrice} IS NULL OR p.price<=#{q.maxPrice})";
    // Whitelisted expressions only: user input is bound as values, never an ORDER BY identifier.
    String ORDER=" ORDER BY CASE WHEN #{q.sortField}='price' AND #{q.sortAsc}=1 THEN p.price END ASC,"
            +"CASE WHEN #{q.sortField}='price' AND #{q.sortAsc}=0 THEN p.price END DESC,"
            +"CASE WHEN #{q.sortField}='salesCount' AND #{q.sortAsc}=1 THEN p.sales_count END ASC,"
            +"CASE WHEN #{q.sortField}='salesCount' AND #{q.sortAsc}=0 THEN p.sales_count END DESC,"
            +"CASE WHEN #{q.sortField}='createTime' AND #{q.sortAsc}=1 THEN p.create_time END ASC,"
            +"CASE WHEN #{q.sortField}='createTime' AND #{q.sortAsc}=0 THEN p.create_time END DESC,p.sort DESC,p.id DESC";
    @Select("SELECT COUNT(*)"+FROM+FILTER)
    Long count(@Param("tenant") Long tenant,@Param("q") AppProductSpuPageReqVO query);
    @Select(FIELDS+FROM+FILTER+ORDER+" LIMIT #{offset},#{limit}")
    List<Row> page(@Param("tenant") Long tenant,@Param("q") AppProductSpuPageReqVO query,@Param("offset") int offset,@Param("limit") int limit);
    @Select(FIELDS+",p.description,CAST(p.slider_pic_urls AS CHAR) AS slider_json,CAST(p.display_properties AS CHAR) AS display_json"+FROM+" AND p.id=#{id}")
    Row detail(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("<script>"+FIELDS+FROM+" AND p.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Row> searchRows(@Param("tenant") Long tenant,@Param("ids") List<Long> ids);
    @Select("SELECT tenant_id,spu_id,id,CAST(properties AS CHAR) AS properties_json,price,market_price,pic_url,stock "
            +"FROM product_sku WHERE tenant_id=#{tenant} AND spu_id=#{spu} AND deleted=b'0' AND status=0 ORDER BY id LIMIT #{limit}")
    List<SkuRow> skus(@Param("tenant") Long tenant,@Param("spu") Long spu,@Param("limit") int limit);
    String OPTION_PRODUCTS="p.tenant_id=#{tenant} AND p.deleted=b'0' AND p.status=1 AND "+SKU_VISIBLE;
    @Select("SELECT c.id,c.parent_id,c.name,c.pic_url FROM product_category c WHERE c.tenant_id=#{tenant} AND c.deleted=b'0' "
            +"AND EXISTS(SELECT 1 FROM product_spu p JOIN product_brand b ON b.tenant_id=p.tenant_id AND b.id=p.brand_id AND b.deleted=b'0' "
            +"JOIN product_category leaf ON leaf.tenant_id=p.tenant_id AND leaf.id=p.category_id AND leaf.deleted=b'0' "
            +"LEFT JOIN product_category parent ON parent.tenant_id=p.tenant_id AND parent.id=leaf.parent_id AND parent.deleted=b'0' "
            +"LEFT JOIN product_category root ON root.tenant_id=p.tenant_id AND root.id=parent.parent_id AND root.deleted=b'0' "
            +"WHERE "+OPTION_PRODUCTS+" AND (leaf.parent_id=0 OR parent.parent_id=0 OR root.parent_id=0) "
            +"AND (c.id=leaf.id OR c.id=parent.id OR c.id=root.id)) ORDER BY c.sort,c.id LIMIT 2001")
    List<AppProductCatalogRespVO.Category> categories(@Param("tenant") Long tenant);
    @Select("SELECT outer_b.id,outer_b.name,outer_b.pic_url FROM product_brand outer_b WHERE outer_b.tenant_id=#{tenant} AND outer_b.deleted=b'0' "
            +"AND EXISTS(SELECT 1"+FROM+" AND p.brand_id=outer_b.id) ORDER BY outer_b.sort,outer_b.id LIMIT 501")
    List<AppProductCatalogRespVO.Brand> brands(@Param("tenant") Long tenant);
    @Data class Row {
        private Long tenantId;private Long id;private String name;private String introduction;
        private Long categoryId;private String categoryName;private Long brandId;private String brandName;
        private String picUrl;private Boolean specType;private Integer price;private Integer marketPrice;
        private Integer stock;private Integer salesCount;private String description;private String sliderJson;private String displayJson;
    }
    @Data class SkuRow {
        private Long tenantId;private Long spuId;private Long id;private String propertiesJson;
        private Integer price;private Integer marketPrice;private String picUrl;private Integer stock;
    }
}
