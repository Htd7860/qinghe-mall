package cn.qinghe.mall.module.trade.controller.app.order.vo;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.util.List;

/** 原成交快照的最小投影，不带当前商品运营/成本/归属/内部单位分摊字段。
 * @author 芋道源码
 */
@Data
public class AppTradeOrderItemRespVO {
    private Long id;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String picUrl;
    private Integer count;
    private Integer refundCount;
    private Integer deliveryCount;
    private Integer price;
    private Long goodsPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private List<ProductTradeSkuRespDTO.Property> properties;
}
