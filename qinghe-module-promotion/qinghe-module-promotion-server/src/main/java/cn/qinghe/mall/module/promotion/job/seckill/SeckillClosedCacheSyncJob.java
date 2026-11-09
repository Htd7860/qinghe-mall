package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 择取原XXL Job的Service委托职责；空/all遍历本地义务，正租户编号定点恢复。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillClosedCacheSyncJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillClosedCacheSyncJob")
    public void execute() { tenants.run("CLOSED_CACHE_SYNC"); }
}
