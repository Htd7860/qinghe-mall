package cn.qinghe.mall.module.trade.job.order;
import cn.qinghe.mall.module.trade.service.order.TradeTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用原租户任务协调器；只发送已提交结果，不创建申请、订单或重置发送预算。 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.trade.seckill-results",name="enabled",havingValue="true")
public class TradeSeckillResultPublishJob {
    private final TradeTenantJobService tenants;
    @XxlJob("tradeSeckillResultPublishJob")
    public void execute() { tenants.run("SECKILL_RESULT_PUBLISH"); }
}
