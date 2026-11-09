package cn.qinghe.mall.module.product.service.search;
import lombok.Data;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
/**
 * 搜索商品的信息，择取macro mall EsProduct销售/品牌/分类/关键词字段。
 * Created by macro on 2018/6/19. Apache-2.0; see third-party/mall-search/NOTICE.md.
 * Modified for trusted tenant, integer cents, persistent visibility tombstone and snapshot version.
 */
@Data
@JsonPropertyOrder({"tenantId","id","version","visible","name","introduction","keywords","brandId","brandName","categoryId","categoryIds","categoryName","picUrl","price","marketPrice","stock","salesCount","sort","createdAt"})
public class EsProduct {
    private Long tenantId;
    private Long id;
    /** 专用投影序号，不可直接使用未覆盖所有变更的SPU authVersion。 */
    private Long version;
    private Boolean visible;
    private String name;
    private String introduction;
    private String keywords;
    private Long brandId;
    private String brandName;
    private Long categoryId;
    private List<Long> categoryIds;
    private String categoryName;
    private String picUrl;
    private Integer price;
    private Integer marketPrice;
    private Integer stock;
    private Integer salesCount;
    private Integer sort;
    private Long createdAt;
}
