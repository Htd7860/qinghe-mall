package cn.qinghe.mall.module.promotion.api.seckill.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 择取原 SeckillValidateJoinRespDTO 的服务端活动价/限购职责；不是占额凭证。 @author HUIHUI */
@Data
public class SeckillCheckoutContextDTO {
    private Long tenantId;
    private Long memberId;
    private Long activityId;
    private Integer generation;
    private Long activityVersion;
    private Long redisEpoch;
    private Long spuId;
    private Long spuVersion;
    private Long skuId;
    private Long skuVersion;
    private Integer priceCent;
    private Integer singleLimit;
    private Integer memberLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime serverTime;
}
