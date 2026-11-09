package cn.qinghe.mall.module.product.api.stock.dto;

import lombok.Data;
import java.util.List;

/**
 * 择取原 ProductSkuUpdateStockReqDTO 的批量 SKU 输入职责。
 * 由 trade 根据持久订单准备记录构造，不能直接绑定浏览器购物车、金额或 userId。
 *
 * @author 芋道源码
 */
@Data
public class ProductOrderStockReqDTO {
    private Long orderId;
    private Long userId;
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
    }
}
