package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillOrderResourceMapper;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillAdmissionRedisDAO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.util.*;

/** 复用原关闭缓存同步职责：终局账本证明→事务外Lua→本库ACK；没有库存重建或TTL释放。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillOrderCacheSyncService {
    private final SeckillOrderResourceMapper resources;
    private final SeckillAdmissionRedisDAO redis;
    private final TransactionTemplate write;
    public SeckillOrderCacheSyncService(SeckillOrderResourceMapper resources,SeckillAdmissionRedisDAO redis,PlatformTransactionManager manager) {
        this.resources=resources;this.redis=redis;write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }
    public void synchronize(String requestNo) {
        Long tenant=context();if (!uuid(requestNo)) throw new IllegalArgumentException("需要原申请号");
        SeckillRequestDO proof=resources.cacheProof(tenant,requestNo);
        if (proof==null) throw new IllegalStateException("原终局及资源流水缺失");
        if ("SYNCED".equals(proof.getResourceCacheState())) return;
        redis.synchronizeOrder(requestNo);
        if (!Objects.equals(context(),tenant)) throw new IllegalStateException("资源同步租户变化");
        Integer done=write.execute(tx -> resources.cacheSynced(proof));
        if (Integer.valueOf(1).equals(done)) return;
        SeckillRequestDO current=resources.cacheProof(tenant,requestNo);
        if (current!=null && "SYNCED".equals(current.getResourceCacheState())
                && Objects.equals(current.getResourceState(),proof.getResourceState())
                && Objects.equals(current.getOrderId(),proof.getOrderId())) return;
        throw new IllegalStateException("Redis原转换已完成，本库同步进度仍待确认");
    }
    /** 调用方保存nextRequest；空页返回null后从头轮转。失败也跨过，防止首条故障阻塞其他记录。 */
    public BatchResult synchronizeBatch(int limit,String after,String trace) {
        Long tenant=context();
        if (limit<1 || limit>20 || after!=null && !uuid(after) || !uuid(trace)) throw new IllegalArgumentException("缓存同步批次或原游标不正确");
        List<SeckillRequestDO> rows=resources.pendingCache(tenant,after,limit);
        if (rows==null || rows.size()>limit) throw new IllegalStateException("原待同步批次不完整");
        BatchResult out=new BatchResult();long started=System.nanoTime();
        if (!rows.isEmpty()) out.nextRequest=after;
        for (SeckillRequestDO row:rows) {
            if (row==null || !Objects.equals(row.getTenantId(),tenant) || !uuid(row.getRequestNo())) throw new IllegalStateException("待同步记录归属不一致");
            if (System.nanoTime()-started>=45000000000L) break;out.attempted++;
            try { synchronize(row.getRequestNo());out.synchronizedCount++; }
            catch (RuntimeException pending) {
                out.pending++;
                log.warn("Seckill order cache pending tenant={},request={},trace={}",tenant,row.getRequestNo(),trace);
            }
            out.nextRequest=row.getRequestNo();
        }
        return out;
    }
    private Long context() {
        SeckillRefundCommandContext.requireCaller();
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()) throw new IllegalStateException("需要可信当前租户");return tenant;
    }
    private boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    @Data public static class BatchResult { private int attempted;private int synchronizedCount;private int pending;private String nextRequest; }
}
