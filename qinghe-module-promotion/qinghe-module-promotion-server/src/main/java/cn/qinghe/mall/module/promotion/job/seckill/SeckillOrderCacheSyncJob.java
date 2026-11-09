package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用既有固定租户任务，原数据库PENDING决定发现；不在Redis存游标。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillOrderCacheSyncJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillOrderCacheSyncJob")
    public void execute() { tenants.run("ORDER_CACHE_SYNC"); }
}
