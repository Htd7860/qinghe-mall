package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 原代次SKU归还的不可变累计目标；PENDING是已预留，不能称商品已入库。 */
@Data
public class SeckillPoolReturnDO {
    private Long tenantId;
    private String returnNo;
    private Long activityId;
    private Integer generation;
    private Long redisEpoch;
    private Long skuId;
    private String allocationOperationNo;
    private String allocationSnapshot;
    private Integer quantity;
    private Integer baseReturned;
    private Integer targetReturned;
    private Long baseProductVersion;
    private String state;
    private Long productVersion;
    private Integer productStatus;
    private String cacheState;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime confirmedTime;
    private Long activeSku;
    private String sourceKind;
    private Long afterSaleId;
}
