package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillOrderResultDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillOrderResultMapper;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillAdmissionRedisDAO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 沿用已有后台原号恢复：Redis成功后才ACK本库进度；短批次游标跨过失败项，不注册新调度框架。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillClosedCacheSyncService {
    private final SeckillOrderResultMapper results;
    private final SeckillAdmissionRedisDAO redis;
    private final TransactionTemplate write;
    public SeckillClosedCacheSyncService(SeckillOrderResultMapper results,SeckillAdmissionRedisDAO redis,PlatformTransactionManager manager) {
        this.results=results;this.redis=redis;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }
    public void synchronize(String eventId) {
        Long tenant=context();
        if (!uuid(eventId)) throw exception0(400,"需要原关闭结果事件号");
        SeckillOrderResultDO receipt=results.receipt(tenant,eventId);
        if (receipt==null || !Integer.valueOf(20).equals(receipt.getResultState()) || receipt.getOrderId()!=null
                || !"SECKILL_REQUEST_CLOSED".equals(receipt.getEventType())) throw exception0(409,"原结果不是建单前永久关闭");
        if ("SYNCED".equals(receipt.getCacheSyncState())) return;
        if (!"PENDING".equals(receipt.getCacheSyncState())) throw exception0(503,"原关闭同步进度不完整");
        redis.releaseClosed(eventId); // 不处于任何数据库事务或行锁内；1/2均表示原释放已完成。
        if (!Objects.equals(context(),tenant)) throw exception0(403,"关闭同步租户上下文已变化");
        Integer acknowledged=write.execute(tx -> results.cacheSynced(tenant,eventId));
        if (Integer.valueOf(1).equals(acknowledged)) return;
        SeckillOrderResultDO current=results.receipt(tenant,eventId);
        if (current!=null && "SYNCED".equals(current.getCacheSyncState()) && Integer.valueOf(20).equals(current.getResultState())) return;
        throw exception0(503,"Redis原释放已完成，同步回执仍待按原事件核对");
    }

    /** 调度方保存返回的游标；空页后从null/null重新扫，失败项也推进，下一轮再按原号重试。 */
    public BatchResult synchronizeBatch(int limit,LocalDateTime afterTime,String afterEvent,String jobTrace) {
        Long tenant=context();
        if (limit<1 || limit>20 || !uuid(jobTrace) || (afterTime==null)!=(afterEvent==null)
                || afterEvent!=null && !uuid(afterEvent) || afterTime!=null && (afterTime.getYear()<2000 || afterTime.getYear()>2099))
            throw exception0(400,"关闭同步批次、UTC游标或trace不正确");
        List<SeckillOrderResultDO> rows=results.pendingCache(tenant,afterTime,afterEvent,limit);
        if (rows==null || rows.size()>limit) throw exception0(503,"关闭同步扫描结果不完整");
        BatchResult result=new BatchResult();long started=System.nanoTime();
        if (!rows.isEmpty()) { result.nextTime=afterTime;result.nextEvent=afterEvent; }
        for (SeckillOrderResultDO row:rows) {
            if (System.nanoTime()-started>=45000000000L) break;
            result.attempted++;
            try { synchronize(row.getEventId());result.synchronizedCount++; }
            catch (RuntimeException pending) {
                result.pending++;
                log.warn("Seckill closed cache synchronization pending tenant={},request={},event={},trace={}",tenant,row.getRequestNo(),row.getEventId(),jobTrace);
            }
            result.nextTime=row.getProcessedAt();result.nextEvent=row.getEventId();
        }
        return result;
    }
    private Long context() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("关闭同步只能在事务外可信后台执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()) throw exception0(403,"关闭同步需要可信当前租户");return tenant;
    }
    private boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    @Data public static class BatchResult {
        private int attempted;
        private int synchronizedCount;
        private int pending;
        private LocalDateTime nextTime;
        private String nextEvent;
    }
}
