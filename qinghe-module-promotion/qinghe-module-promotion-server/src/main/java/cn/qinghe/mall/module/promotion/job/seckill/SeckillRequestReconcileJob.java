package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用原超时Job入口，按本地原义务发现，不在Job里推断跨服务订单结果。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-events",name="enabled",havingValue="true")
public class SeckillRequestReconcileJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillRequestReconcileJob")
    public void execute() { tenants.run("REQUEST_RECONCILE"); }
}
