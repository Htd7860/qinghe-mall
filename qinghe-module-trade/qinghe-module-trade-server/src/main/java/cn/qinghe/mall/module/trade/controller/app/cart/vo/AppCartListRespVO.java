package cn.qinghe.mall.module.trade.controller.app.cart.vo;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.util.List;
/** 原valid/invalid商品列表职责；不自动删掉失效项，不返回伪造的旧价。
 * @author 芋道源码
 */
@Data
public class AppCartListRespVO {
    private Long version;
    private Boolean canPurchase;
    private Integer maxCartLines;
    private Integer maxItemQuantity;
    private Integer maxOrderLines;
    private List<Line> lines;
    @Data public static class Line {
        private Long id;
        private Long spuId;
        private Long skuId;
        private Integer count;
        private Boolean selected;
        private Long authVersion;
        private Boolean valid;
        private String invalidReason;
        private ProductTradeSkuRespDTO product;
    }
}
