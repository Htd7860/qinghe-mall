package cn.qinghe.mall.module.promotion.api.coupon.dto;
import lombok.Data;
/** 原TradePriceCalculateRespBO.OrderItem券计算所需字段。
 * 仅由trade读取权威商品/订单准备数据后构造，不能从浏览器金额直接绑定。
 * @author 芋道源码
 */
@Data
public class CouponOrderLineDTO {
    private Long lineId;
    private Long spuId;
    private Long skuId;
    private Long categoryId;
    private Integer quantity;
    /** 权威商品单价，整数分；不含运费、不含本张券。 */
    private Long unitPrice;
}
