package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 仅六个固定秒杀任务的分片遍历进度（UTC）；不是业务恢复预算或通用流程记录。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class SeckillTenantJobCursorDO {
    private String jobCode;
    private Integer shardIndex;
    private Integer shardTotal;
    private Long lastTenantId;
    private Long ceilingTenantId;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime updateTime;
}
