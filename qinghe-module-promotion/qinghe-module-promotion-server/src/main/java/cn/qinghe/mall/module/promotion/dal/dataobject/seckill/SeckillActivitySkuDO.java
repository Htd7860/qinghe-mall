package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 择取原 SeckillProductDO 活动规格结构；权威额度池没有逻辑删除列。 @author HUIHUI */
@Data
public class SeckillActivitySkuDO {
    private Long id;
    private Long tenantId;
    private Long activityId;
    private Long skuId;
    private Long skuVersion;
    private String specSnapshot;
    private Integer priceCent;
    private Integer plannedQuantity;
    private Integer allocatedQuantity;
    private Integer availableQuantity;
    private Integer heldQuantity;
    private Integer soldQuantity;
    private Integer returnedQuantity;
    private Integer returnPendingQuantity;
    private Integer allocationStatus;
    private String allocationOperationNo;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
