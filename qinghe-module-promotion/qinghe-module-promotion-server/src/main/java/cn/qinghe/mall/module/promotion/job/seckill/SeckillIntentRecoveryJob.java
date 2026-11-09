package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用固定XXL/原义务租户协调器，只有已持久裁决才同步缓存。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-events",name="enabled",havingValue="true")
public class SeckillIntentRecoveryJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillIntentRecoveryJob")
    public void execute() { tenants.run("INTENT_RECOVERY"); }
}
