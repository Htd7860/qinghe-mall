package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 原 SeckillActivityDO 活动配置职责；本项目状态/发布代次与 UTC 字段显式映射。 @author halfninety */
@Data
public class SeckillActivityDO {
    private Long id;
    private Long tenantId;
    private String activityNo;
    private String name;
    private Long productSpuId;
    private Long productSpuVersion;
    private String productName;
    private String picUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer singleLimit;
    private Integer memberLimit;
    private Integer status;
    private Long version;
    private Integer generation;
    private Long redisEpoch;
    private String publishOperationNo;
    private String stopReason;
    private String stoppedBy;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Boolean deleted;
}
