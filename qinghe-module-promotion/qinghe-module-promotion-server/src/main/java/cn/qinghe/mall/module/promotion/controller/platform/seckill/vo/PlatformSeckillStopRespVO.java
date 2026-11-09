package cn.qinghe.mall.module.promotion.controller.platform.seckill.vo;
import lombok.Data;

/** DB停止的原版本回执；不冒称当前活动仍是该版本或Redis关闭完成。 */
@Data
public class PlatformSeckillStopRespVO {
    private Long tenantId;
    private Long id;
    private Integer generation;
    private Long stoppedVersion;
    private String cacheCloseState;
}
