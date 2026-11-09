package cn.qinghe.mall.module.product.controller.app.spu.vo;
import lombok.Data;
/** 原AppProductSpuRespVO销售字段；不投影成本、归属、条码和内部配送配置。 @author 芋道源码 */
@Data
public class AppProductSpuRespVO {
    private Long id;
    private String name;
    private String introduction;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String picUrl;
    private Boolean specType;
    private Integer price;
    private Integer marketPrice;
    private Integer stock;
    private Integer salesCount;
    /** 店铺经营/库存的展示提示，不能代替下单最终核对。 */
    private Boolean canPurchase;
}
