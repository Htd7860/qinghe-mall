package cn.qinghe.mall.module.product.job.search;
import cn.qinghe.mall.module.product.service.search.ProductSearchTenantJobs;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 择取原固定XXL Handler委托写法；无Spring定时扫描或启动期执行。 @author 芋道源码 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="qinghe.product.search",name="enabled",havingValue="true")
public class ProductSearchJob {
    private final ProductSearchTenantJobs jobs;
    @XxlJob("productSearchSyncJob")
    public void synchronize(){jobs.run(false);}
    /** 只作受控单租户离线准备，不调度为周期性重建。 */
    @XxlJob("productSearchRebuildJob")
    public void rebuild(){jobs.run(true);}
}
