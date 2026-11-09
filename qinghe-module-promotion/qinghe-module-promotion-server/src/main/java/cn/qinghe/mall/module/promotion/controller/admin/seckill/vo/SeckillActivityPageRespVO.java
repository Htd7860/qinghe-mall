package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import lombok.Data;
import java.time.OffsetDateTime;

/** 活动列表只返回主表和本页规格额度汇总，完整规格由get读取，不包含发布操作键。 */
@Data
public class SeckillActivityPageRespVO {
    private Long id;
    private String activityNo;
    private String name;
    private Long productSpuId;
    private String productName;
    private String picUrl;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer singleLimit;
    private Integer memberLimit;
    private Integer status;
    private Long version;
    private Integer generation;
    private Long serverTime;
    private Long skuCount;
    private Long plannedQuantity;
    private Long allocatedQuantity;
    private Long availableQuantity;
    private Long heldQuantity;
    private Long soldQuantity;
    private Long returnedQuantity;
    private Long returnPendingQuantity;
}
