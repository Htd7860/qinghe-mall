package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 不可变发布意图和原规格请求，后续恢复不能重新读取草稿构造不同库存参数。 */
@Data
public class SeckillPublishRecordDO {
    private Long tenantId;
    private String requestId;
    private Long activityId;
    private Long actorId;
    private Long fromVersion;
    private Long preparedVersion;
    private Integer generation;
    private String requestSnapshot;
    private String configurationSnapshot;
    private LocalDateTime createTime;
}
