package cn.qinghe.mall.module.trade.controller.admin.order.vo;

import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.util.List;

/** 原成交商品投影，无单位退款分摊/库存内部字段。
 * @author 芋道源码
 */
@Data
public class TradeOrderItemRespVO {
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
