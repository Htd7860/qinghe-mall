package cn.qinghe.mall.module.product.api.seckill.dto;

import lombok.Data;

/** 当前已提交的划拨凭证；quantity 是计划量，allocatedQuantity 才是实际划拨量。 */
@Data
public class ProductSeckillAllocationRespDTO {
    public static final int PENDING = 0;
    public static final int ALLOCATED = 10;
    public static final int COMPENSATED = 20;
    public static final int RETURNED = 30;
    public static final int REVIEW = 40;
    public static final int RETURNING = 50;

    private String activityNo;
    private Integer generation;
    private String operationNo;
    private Long spuId;
    private Long skuId;
    private Long spuVersion;
    private Long skuVersion;
    private Integer quantity;
    private Integer allocatedQuantity;
    private Integer returnedQuantity;
    private Integer status;
    private Long version;
}
