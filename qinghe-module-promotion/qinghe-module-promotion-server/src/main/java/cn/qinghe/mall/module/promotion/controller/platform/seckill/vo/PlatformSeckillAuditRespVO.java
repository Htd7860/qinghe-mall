package cn.qinghe.mall.module.promotion.controller.platform.seckill.vo;
import lombok.Data;
import java.time.OffsetDateTime;

/** 只投影动作/版本/操作者，不把details中的库存操作号或初始化资料公开。 */
@Data
public class PlatformSeckillAuditRespVO {
    private Long id;
    private Long actorId;
    private String actorRealm;
    private String action;
    private Long fromVersion;
    private Long toVersion;
    private OffsetDateTime createTime;
}
