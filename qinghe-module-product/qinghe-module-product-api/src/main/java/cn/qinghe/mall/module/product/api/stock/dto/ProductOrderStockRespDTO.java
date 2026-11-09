package cn.qinghe.mall.module.product.api.stock.dto;

import lombok.Data;
import java.util.List;

/** 已提交的库存结果；DEDUCTED 不代表订单或支付已经成功。 */
@Data
public class ProductOrderStockRespDTO {
    public static final int CLOSED = 0;
    public static final int DEDUCTED = 1;
    public static final int RELEASED = 2;

    private Long orderId;
    private Long userId;
    private Integer status;
    private String releaseReason;
    private List<Item> items;

    @Data
    public static class Item {
        private Long orderItemId;
        private Long spuId;
        private Long skuId;
        private Integer quantity;
        private Integer unitPrice;
        private Long spuVersion;
        private Long skuVersion;
        private Integer beforeStock;
        private Integer afterStock;
        private Integer releaseBeforeStock;
        private Integer releaseAfterStock;
    }
}
