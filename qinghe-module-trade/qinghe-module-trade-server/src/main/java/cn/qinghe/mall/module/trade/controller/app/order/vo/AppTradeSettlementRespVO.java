package cn.qinghe.mall.module.trade.controller.app.order.vo;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.util.List;
/** 最小本人报价视图，不向前端发送后台配送费率/券user/服务端摘要或绑定控制字段。
 * @author 芋道源码
 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class AppTradeSettlementRespVO {
    private String quoteId;
    private String requestId;
    private Long cartVersion;
    private Long addressId;
    private Long couponId;
    private String couponName;
    private Long serverNow;
    private Long expiresAt;
    private Boolean canPurchase;
    private Boolean expired;
    private Boolean cartChanged;
    private Boolean bound;
    private Boolean snapshotPresent;
    private Long goodsPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private Address address;
    private List<Line> items;
    @Data public static class Line {
        private Long cartId;
        private Long cartLineVersion;
        private Integer count;
        private ProductTradeSkuRespDTO product;
        private Long goodsPrice;
        private Long discountPrice;
        private Long deliveryPrice;
        private Long payPrice;
    }
    @Data @lombok.ToString(onlyExplicitlyIncluded=true) public static class Address {
        private Long id;
        private Long authVersion;
        private String name;
        private String mobile;
        private Integer areaId;
        private String areaName;
        private String detailAddress;
    }
}
