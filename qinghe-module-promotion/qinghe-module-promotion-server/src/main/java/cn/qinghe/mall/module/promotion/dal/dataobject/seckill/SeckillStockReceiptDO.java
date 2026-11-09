package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 不可变的product原划拨版本回执，不以网络超时推断成功或失败。 */
@Data
public class SeckillStockReceiptDO {
    private Long tenantId;
    private Long activityId;
    private Integer generation;
    private Long skuId;
    private Long productVersion;
    private Integer productStatus;
    private Integer allocatedQuantity;
    private Integer returnedQuantity;
    private LocalDateTime createTime;
}
