package cn.qinghe.mall.module.trade.dal.dataobject.order;

import lombok.Data;

/** 择取原TradeOrderItemDO商品/规格/价格字段；稳定项id和单位分摊供后续退款读取。
 * properties为原商品属性JSON，不从当前商品重新拼接历史规格。
 * @author 芋道源码
 */
@Data
public class TradeOrderItemDO {
    private Long id;
    /** 0 普通购物车订单；1 秒杀。秒杀没有购物车项。 */
    private Integer sourceType;
    private Long tenantId;
    private Long orderId;
    private Long userId;
    private Long cartId;
    private Long spuId;
    private String spuName;
    private Long skuId;
    private Long spuVersion;
    private Long skuVersion;
    private String properties;
    private String picUrl;
    private Integer count;
    /** 已全额退款并停止履约的单位数，后续售后按原单位分摊维护。 */
    private Integer refundCount;
    /** 本次唯一发货实际包含的数量，之后退款不覆盖原发货事实。 */
    private Integer deliveryCount;
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
