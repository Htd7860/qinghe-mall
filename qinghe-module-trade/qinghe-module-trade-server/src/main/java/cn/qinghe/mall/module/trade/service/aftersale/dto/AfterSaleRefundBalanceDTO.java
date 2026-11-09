package cn.qinghe.mall.module.trade.service.aftersale.dto;
import lombok.Data;
import lombok.Value;

/** 同一原订单父锁内的既有退款账汇总，SQL聚合不把海量原单位加载到内存。 */
@Data
public class AfterSaleRefundBalanceDTO {
    private Long successGoods;
    private Long successDelivery;
    private Long pendingDelivery;
    @Value
    public static class Totals {
        long refundedQuantity;
        long refundedGoods;
        long reservedGoods;
    }
    @Data
    public static class Units {
        private Long orderItemId;
        private String state;
        private Long quantity;
        private Long goodsPrice;
    }
}
