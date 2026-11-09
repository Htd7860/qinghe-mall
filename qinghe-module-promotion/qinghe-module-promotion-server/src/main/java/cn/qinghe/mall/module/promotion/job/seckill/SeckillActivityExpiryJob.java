package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用固定租户任务，系统到期停止与关闭缓存恢复；不提前结清原库存/资金义务。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillActivityExpiryJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillActivityExpiryJob")
    public void execute() { tenants.run("ACTIVITY_EXPIRE"); }
}
