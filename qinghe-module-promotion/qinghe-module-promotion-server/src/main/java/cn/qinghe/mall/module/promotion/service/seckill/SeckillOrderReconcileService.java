package cn.qinghe.mall.module.promotion.service.seckill;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillOrderReconcileMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.util.*;

/** 复用既有固定任务逐笔委托：未付正常等待，异常十次待核查，不按超时推断取消。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillOrderReconcileService {
    private final SeckillOrderReconcileMapper records;
    private final SeckillOrderResourceService resources;
    private final TransactionTemplate write;
    public SeckillOrderReconcileService(SeckillOrderReconcileMapper records,SeckillOrderResourceService resources,PlatformTransactionManager manager) {
        this.records=records;this.resources=resources;write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }
    public Result reconcile(int limit,String trace) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("订单终局核对仅限事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || limit<1 || limit>20
                || trace==null || !trace.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException("核对批次上下文不正确");
        List<SeckillRequestDO> rows=records.due(tenant,limit);
        if(rows==null || rows.size()>limit)throw new IllegalStateException("原订单核对批次不完整");
        Result out=new Result();long started=System.nanoTime();
        for(SeckillRequestDO row:rows) {
            if(row==null || !Objects.equals(row.getTenantId(),tenant))throw new IllegalStateException("原核对批次租户不一致");
            if(System.nanoTime()-started>=45000000000L)break;out.attempted++;
            try {
                if(resources.synchronize(row.getRequestNo()))out.completed++;
                else {write.executeWithoutResult(tx -> records.defer(row,false));out.waiting++;}
            } catch(RuntimeException unknown) {
                out.failed++;
                try {write.executeWithoutResult(tx -> records.defer(row,true));}
                catch(RuntimeException unavailable){log.warn("Seckill order check metadata pending tenant={},request={},trace={}",tenant,row.getRequestNo(),trace);}
                log.warn("Seckill order check pending tenant={},request={},trace={}",tenant,row.getRequestNo(),trace);
            }
        }
        out.manual=records.manual(tenant);
        if(out.manual>0)log.error("Seckill order checks need original-state reconciliation tenant={},manual={},trace={}",tenant,out.manual,trace);
        return out;
    }
    @Data public static class Result { private int attempted;private int completed;private int waiting;private int failed;private long manual; }
}
