package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import java.time.LocalDateTime;
/** 单租户关闭结果/订单终局/闭场归还及清算独立扫描位置；版本只保护游标，不是业务重试预算。 */
@Data
public class SeckillCacheJobCursorDO {
    private Long tenantId;
    private LocalDateTime afterTime;
    private String afterEvent;
    private String afterRequest;
    private String afterReturn;
    private Long afterPoolId;
    private Long afterExpiryActivity;
    private Long afterClearingActivity;
    private Long version;
    private LocalDateTime updateTime;
}
