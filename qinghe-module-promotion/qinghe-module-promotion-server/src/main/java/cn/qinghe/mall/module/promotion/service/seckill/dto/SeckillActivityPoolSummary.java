package cn.qinghe.mall.module.promotion.service.seckill.dto;

import lombok.Data;

/** 只汇总本页活动ID，不加载规格JSON或扫描其他租户。 */
@Data
public class SeckillActivityPoolSummary {
    private Long activityId;
    private Long skuCount;
    private Long plannedQuantity;
    private Long allocatedQuantity;
    private Long availableQuantity;
    private Long heldQuantity;
    private Long soldQuantity;
    private Long returnedQuantity;
    private Long returnPendingQuantity;
}
