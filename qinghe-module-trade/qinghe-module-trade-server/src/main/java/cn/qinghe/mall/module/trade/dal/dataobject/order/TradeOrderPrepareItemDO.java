package cn.qinghe.mall.module.trade.dal.dataobject.order;

import lombok.Data;

/**
 * 择取原 TradeOrderItemDO 的商品、规格、数量及成交分摊快照。
 * 准备项 id 稳定供后续库存/优惠券调用；不在退款时按新价格重算原分摊。
 *
 * @author 芋道源码
 */
@Data
public class TradeOrderPrepareItemDO {
    private Long id;
    /** 0 普通购物车订单；1 秒杀。秒杀没有购物车项。 */
    private Integer sourceType;
    private Long tenantId;
    private Long preparationId;
    private Long cartId;
    private Long cartLineVersion;
    private Long spuId;
    private String spuName;
    private Long skuId;
    private Long spuVersion;
    private Long skuVersion;
    private String properties;
    private String picUrl;
    private Integer count;
    private Integer price;
    private Long goodsPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private Long unitDiscountBase;
    private Integer unitDiscountRemainder;
    private Long unitDeliveryBase;
    private Integer unitDeliveryRemainder;
}
