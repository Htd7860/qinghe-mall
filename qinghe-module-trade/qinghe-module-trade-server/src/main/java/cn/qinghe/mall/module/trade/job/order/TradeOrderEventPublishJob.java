package cn.qinghe.mall.module.trade.job.order;
import cn.qinghe.mall.module.trade.service.order.TradeTenantJobService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 原XXL委托职责；空参数/all遍历本服务原义务租户，正编号保留单租户修复。
 * @author 芋道源码
 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.trade.events",name="enabled",havingValue="true")
public class TradeOrderEventPublishJob {
    private final TradeTenantJobService tenants;
    @XxlJob("tradeOrderEventPublishJob")
    public void execute() {tenants.run("OUTBOX_PUBLISH");}
}
