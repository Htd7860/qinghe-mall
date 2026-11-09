package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import lombok.Data;
import java.util.List;

/** 原发布代次的只读进度，不暴露库存操作号、运行许可或内部请求快照。 */
@Data
public class SeckillClearingProgressRespVO {
    private Long id;
    private Integer generation;
    private Integer currentGeneration;
    private Integer currentStatus;
    private String basis;
    private Boolean runtimeClosed;
    private Boolean baselineSaved;
    private Boolean clearingCompleted;
    private Boolean draftReopened;
    private Boolean paidQuotaConsistent;
    private Pending pending;
    private List<Sku> skus;
    private Long serverTime;

    @Data public static class Pending {
        private Boolean admission;
        private Boolean requests;
        private Boolean outbox;
        private Boolean resultCache;
        private Boolean refunds;
        private Boolean poolReturns;
        private Boolean memberHeld;
    }
    @Data public static class Sku {
        private Long skuId;
        private Integer allocatedQuantity;
        private Integer availableQuantity;
        private Integer heldQuantity;
        private Integer soldQuantity;
        private Integer returnedQuantity;
        private Integer returnPendingQuantity;
        private Long historicalReturnedQuantity;
        private Boolean receiptConsistent;
    }
}
