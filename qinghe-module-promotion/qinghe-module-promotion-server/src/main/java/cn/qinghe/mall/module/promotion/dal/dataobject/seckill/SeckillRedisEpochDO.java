package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 首次epoch：0准备、10确认seed、15初始化窗口、20就绪、30运行后停止、40未发运行许可已关闭。 */
@Data
public class SeckillRedisEpochDO {
    private Long tenantId;
    private Long activityId;
    private Integer generation;
    private Long epoch;
    private String publishRequestId;
    private Long actorId;
    private Long fromVersion;
    private Long toVersion;
    private String definitionSnapshot;
    private String definitionDigest;
    private Integer status;
    private LocalDateTime createTime;
    private String initToken;
    private LocalDateTime initDeadline;
    private String stopCacheState;
}
