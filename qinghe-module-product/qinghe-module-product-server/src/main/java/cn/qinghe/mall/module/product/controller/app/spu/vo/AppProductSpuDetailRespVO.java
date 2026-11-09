package cn.qinghe.mall.module.product.controller.app.spu.vo;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
/** 择取原详情与SKU白名单；description仅供文本展示，不可未经净化v-html。 @author 芋道源码 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppProductSpuDetailRespVO extends AppProductSpuRespVO {
    private String description;
    private List<String> sliderPicUrls;
    private List<ProductTradeSkuRespDTO.Property> displayProperties;
    private List<Sku> skus;
    @Data public static class Sku {
        private Long id;
        private List<ProductTradeSkuRespDTO.Property> properties;
        private Integer price;
        private Integer marketPrice;
        private String picUrl;
        private Integer stock;
    }
}
