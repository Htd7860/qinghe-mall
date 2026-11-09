package cn.qinghe.mall.module.promotion.job.seckill;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** 原固定XXL委托，只有真实订单证明才转换资源。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillOrderReconcileJob {
    private final SeckillTenantJobService tenants;
    @XxlJob("seckillOrderReconcileJob")
    public void execute() { tenants.run("ORDER_RESOURCE_SYNC"); }
}
