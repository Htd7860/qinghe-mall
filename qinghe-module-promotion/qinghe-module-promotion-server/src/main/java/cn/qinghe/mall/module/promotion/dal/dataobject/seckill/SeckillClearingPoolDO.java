package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import java.time.LocalDateTime;

/** 原代次进入清算时的不可变余额基线；sold仍代表未来可能发生退款的原义务。 */
@Data
public class SeckillClearingPoolDO {
    private Long tenantId;
    private Long activityId;
    private Integer generation;
    private Long redisEpoch;
    private Long skuId;
    private String allocationOperationNo;
    private String allocationSnapshot;
    private Integer allocatedQuantity;
    private Integer soldQuantity;
    private Integer returnedQuantity;
    private Long productVersion;
    private Integer productStatus;
    private String definitionDigest;
    private Long poolVersion;
    private Long activityVersion;
    private LocalDateTime createdAt;
}
