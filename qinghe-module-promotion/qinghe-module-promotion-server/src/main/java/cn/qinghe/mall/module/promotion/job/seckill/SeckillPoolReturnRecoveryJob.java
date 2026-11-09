package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用固定租户任务：原号归还恢复、空闲池归还准备、原代次清算；不依赖租户启用状态。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillPoolReturnRecoveryJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillPoolReturnRecoveryJob")
    public void execute() { tenants.run("POOL_RETURN_RECOVERY"); }
}
