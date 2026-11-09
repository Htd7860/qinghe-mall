package cn.qinghe.mall.module.trade.dal.dataobject.order;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 仅普通恢复/发送与秒杀结果发送/绑定清理的分片遍历进度；不是业务恢复预算或通用流程记录。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeTenantJobCursorDO {
    private String jobCode;
    private Integer shardIndex;
    private Integer shardTotal;
    private Long lastTenantId;
    private Long ceilingTenantId;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime updateTime;
}
