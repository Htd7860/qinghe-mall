package cn.qinghe.mall.module.trade.job.order;
import cn.qinghe.mall.module.trade.service.order.TradeTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用原过期任务的委托职责，只清建单前栅栏和原密文。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
public class TradeSeckillBindingCleanupJob {
    private final TradeTenantJobService tenants;
    @XxlJob("tradeSeckillBindingCleanupJob")
    public void execute() { tenants.run("SECKILL_BINDING_CLEANUP"); }
}
