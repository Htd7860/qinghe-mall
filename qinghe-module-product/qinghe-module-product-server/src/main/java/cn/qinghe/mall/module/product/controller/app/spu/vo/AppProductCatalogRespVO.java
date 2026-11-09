package cn.qinghe.mall.module.product.controller.app.spu.vo;
import lombok.Data;
import java.util.List;
/** 当前可见商品引用的分类（含祖先）及品牌，不返回管理状态/版本/内部说明。 */
@Data
public class AppProductCatalogRespVO {
    private List<Category> categories;
    private List<Brand> brands;
    @Data public static class Category {private Long id;private Long parentId;private String name;private String picUrl;}
    @Data public static class Brand {private Long id;private String name;private String picUrl;}
}
