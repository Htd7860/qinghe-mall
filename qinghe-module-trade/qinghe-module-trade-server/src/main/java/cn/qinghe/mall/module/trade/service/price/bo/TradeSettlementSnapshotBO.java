package cn.qinghe.mall.module.trade.service.price.bo;
import cn.qinghe.mall.module.member.api.address.dto.MemberAddressRespDTO;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.service.delivery.bo.TradeDeliveryQuoteBO;
import lombok.Data;
import java.util.List;
/** 原TradePriceCalculateRespBO的选定流程快照；内部保存权威规则与单位分摊，非浏览器可写对象。
 * 包含本人收货隐私，不记录到日志/额外客户端存储。
 * @author 芋道源码
 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class TradeSettlementSnapshotBO {
    private MemberAddressRespDTO address;
    private MemberStorefrontRespDTO storefront;
    private CouponRespDTO coupon;
    private CouponQuoteDTO couponQuote;
    private TradeDeliveryQuoteBO delivery;
    private List<Item> items;
    private Long goodsPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    @Data public static class Item {
        private Long cartId;
        private Long cartLineVersion;
        private Integer count;
        private ProductTradeSkuRespDTO product;
        private Long goodsPrice;
        private Long discountPrice;
        private Long deliveryPrice;
        private Long payPrice;
        private Long unitDiscountBase;
        private Integer unitDiscountRemainder;
        private Long unitDeliveryBase;
        private Integer unitDeliveryRemainder;
    }
}
