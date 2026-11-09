package cn.qinghe.mall.module.promotion.service.seckill.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 发布意图历史与原UUID不可变决议，不包含每SKU库存操作键或完整配置/身份凭据。 */
@Data
public class SeckillPublishHistoryDTO {
    private String requestId;
    private Long actorId;
    private Long fromVersion;
    private Long preparedVersion;
    private Integer generation;
    /** SQL写入UTC；HTTP历史响应VO显式转换为UTC偏移，不能按浏览器本地时区解释。 */
    private LocalDateTime preparedAt;
    private Boolean published;
    private Boolean withdrawn;
    private Boolean settled;
}
