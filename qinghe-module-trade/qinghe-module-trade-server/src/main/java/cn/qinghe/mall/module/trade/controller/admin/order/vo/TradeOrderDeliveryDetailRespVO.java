package cn.qinghe.mall.module.trade.controller.admin.order.vo;

import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import lombok.ToString;
import java.util.List;

/** 仅发货权限读取已付款待发货且没有进行中售后的原收件快照；不带财务/会员字段。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderDeliveryDetailRespVO {
    private Long id;
    private String no;
    private Long authVersion;
    private String receiverName;
    private String receiverMobile;
    private Integer receiverAreaId;
    private String receiverAreaName;
    private String receiverDetailAddress;
    private String userRemark;
    private List<Item> items;
    @Data public static class Item {
        private Long id;
        private Long skuId;
        private String spuName;
        private Integer count;
        private Integer refundCount;
        private Integer deliveryCount;
        private List<ProductTradeSkuRespDTO.Property> properties;
    }
}
