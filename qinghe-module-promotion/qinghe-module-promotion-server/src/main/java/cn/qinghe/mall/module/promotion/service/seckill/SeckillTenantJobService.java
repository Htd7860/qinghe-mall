package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillTenantJobCursorDO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillCacheJobCursorDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillTenantJobCursorMapper;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.util.*;

/** 择取原TenantJobAspect逐租户委托/异常隔离/上下文清理职责。
 * 仅八个固定秒杀任务；本地原义务发现+固定游标，不依赖system租户启用状态。
 * @author 芋道源码
 */
@Service
public class SeckillTenantJobService {
    private final SeckillTenantJobCursorMapper cursors;
    private final ObjectProvider<SeckillEventPublisher> publishers;
    private final ObjectProvider<SeckillClosedCacheSyncService> caches;
    private final ObjectProvider<SeckillRequestReconcileService> reconciliations;
    private final ObjectProvider<SeckillIntentRecoveryService> intentRecoveries;
    private final ObjectProvider<SeckillOrderCacheSyncService> orderCaches;
    private final ObjectProvider<SeckillOrderReconcileService> orderReconciliations;
    private final ObjectProvider<SeckillPoolReturnService> poolReturns;
    private final ObjectProvider<SeckillActivityExpiryService> expiries;
    private final ObjectProvider<SeckillClearingService> clearings;
    private final TransactionTemplate write;
    public SeckillTenantJobService(SeckillTenantJobCursorMapper cursors,ObjectProvider<SeckillEventPublisher> publishers,
            ObjectProvider<SeckillClosedCacheSyncService> caches,ObjectProvider<SeckillRequestReconcileService> reconciliations,
            ObjectProvider<SeckillIntentRecoveryService> intentRecoveries,ObjectProvider<SeckillOrderCacheSyncService> orderCaches,
            ObjectProvider<SeckillOrderReconcileService> orderReconciliations,ObjectProvider<SeckillPoolReturnService> poolReturns,
            ObjectProvider<SeckillActivityExpiryService> expiries,ObjectProvider<SeckillClearingService> clearings,PlatformTransactionManager manager) {
        this.cursors=cursors;this.publishers=publishers;this.caches=caches;this.reconciliations=reconciliations;this.intentRecoveries=intentRecoveries;
        this.orderCaches=orderCaches;
        this.orderReconciliations=orderReconciliations;
        this.poolReturns=poolReturns;
        this.expiries=expiries;
        this.clearings=clearings;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }

    public void run(String code) {
        if(RequestContextHolder.getRequestAttributes()!=null || SecurityContextHolder.getContext().getAuthentication()!=null
                || TransactionSynchronizationManager.isActualTransactionActive() || XxlJobContext.getXxlJobContext()==null
                || XxlJobHelper.getJobId()<=0)throw new IllegalStateException("全租户秒杀任务仅允许可信XXL后台上下文");
        if(!Arrays.asList("SECKILL_COMMAND_PUBLISH","CLOSED_CACHE_SYNC","REQUEST_RECONCILE","INTENT_RECOVERY","ORDER_CACHE_SYNC","ORDER_RESOURCE_SYNC","POOL_RETURN_RECOVERY","ACTIVITY_EXPIRE").contains(code))throw new IllegalArgumentException("不支持的秒杀任务");
        TenantContextHolder.clear();SecurityContextHolder.clearContext();clearLog();
        String trace=UUID.randomUUID().toString();MDC.put("traceId",trace);
        SeckillTenantJobCursorDO cursor=null;int attempted=0,failed=0;
        try {
            int shard=XxlJobHelper.getShardIndex(),total=XxlJobHelper.getShardTotal();
            if(total<1 || total>128 || shard<0 || shard>=total)throw new IllegalArgumentException("XXL分片范围不正确");
            if("SECKILL_COMMAND_PUBLISH".equals(code) && publishers.getIfAvailable()==null)throw new IllegalStateException("事件发布未启用");
            if("CLOSED_CACHE_SYNC".equals(code) && caches.getIfAvailable()==null)throw new IllegalStateException("关闭缓存恢复未启用");
            if("REQUEST_RECONCILE".equals(code) && reconciliations.getIfAvailable()==null)throw new IllegalStateException("申请超时协调未启用");
            if("INTENT_RECOVERY".equals(code) && intentRecoveries.getIfAvailable()==null)throw new IllegalStateException("受理意图恢复未启用");
            if("ORDER_CACHE_SYNC".equals(code) && orderCaches.getIfAvailable()==null)throw new IllegalStateException("订单缓存恢复未启用");
            if("ORDER_RESOURCE_SYNC".equals(code) && orderReconciliations.getIfAvailable()==null)throw new IllegalStateException("订单终局核对未启用");
            if("POOL_RETURN_RECOVERY".equals(code) && (poolReturns.getIfAvailable()==null || clearings.getIfAvailable()==null))
                throw new IllegalStateException("闭场归还或清算未启用");
            if("ACTIVITY_EXPIRE".equals(code) && expiries.getIfAvailable()==null)throw new IllegalStateException("活动到期停止未启用");
            String input=XxlJobHelper.getJobParam();
            if(input!=null && !input.isEmpty() && !"all".equals(input)) {
                if(!input.matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException("任务参数只允许all或原单个正租户编号");
                long tenant=Long.parseLong(input);
                if(tenant>9_007_199_254_740_991L)throw new IllegalArgumentException("租户编号超出范围");
                if(tenant%total==shard) {attempted++;if(!perform(code,tenant,trace))failed++;}
            } else {
                cursor=claim(code,shard,total);
                if(cursor==null)throw new IllegalStateException("当前任务分片由其他执行者持有");
                List<Long> tenants=cursors.tenants(code,cursor.getLastTenantId(),cursor.getCeilingTenantId(),shard,total);
                validate(tenants,cursor);
                if(tenants.isEmpty())advance(cursor,cursor.getCeilingTenantId());
                long started=System.nanoTime();
                for(Long tenant:tenants) {
                    // 不开无界parallelStream；轮转进度持久保存，下次从后续租户续行。
                    if(System.nanoTime()-started>=45_000_000_000L)break;
                    attempted++;if(!perform(code,tenant,trace))failed++;
                    // 失败租户也推进遍历位置，避免它长期阻塞后续租户；原业务失败预算不重置。
                    advance(cursor,tenant);
                }
                if(!tenants.isEmpty() && tenants.size()<5 && attempted==tenants.size())advance(cursor,cursor.getCeilingTenantId());
            }
            XxlJobHelper.log("seckill-job={}, shard={}/{}, tenants={}, failed={}, trace={}",code,shard,total,attempted,failed,trace);
            if(failed>0)XxlJobHelper.handleFail("秒杀租户批次存在失败/人工记录，trace="+trace);
            else XxlJobHelper.handleSuccess("秒杀租户批次完成，tenants="+attempted+", trace="+trace);
        } catch(RuntimeException error) {
            XxlJobHelper.log("秒杀租户批次未完成 code={}, tenants={}, trace={}",code,attempted,trace);
            XxlJobHelper.handleFail("秒杀租户批次未完成，trace="+trace);
        } finally {
            TenantContextHolder.clear();SecurityContextHolder.clearContext();
            if(cursor!=null) {
                SeckillTenantJobCursorDO owned=cursor;
                try {write.executeWithoutResult(tx -> cursors.release(owned.getJobCode(),owned.getShardIndex(),owned.getShardTotal(),owned.getLeaseOwner(),now()));}
                catch(RuntimeException pending) {
                    XxlJobHelper.log("秒杀任务游标释放待核对 code={}, trace={}",code,trace);
                    XxlJobHelper.handleFail("秒杀任务游标释放待核对，trace="+trace);
                }
            }
            clearLog();
        }
    }

    private boolean perform(String code,Long tenant,String trace) {
        MDC.put("tenantId",tenant.toString());
        try {
            return TenantUtils.execute(tenant,() -> {
                if("SECKILL_COMMAND_PUBLISH".equals(code)) {
                    SeckillEventPublisher publisher=publishers.getIfAvailable();
                    if(publisher==null)throw new IllegalStateException("命令发布未启用");
                    SeckillEventPublisher.Result result=publisher.publish(5,trace);
                    XxlJobHelper.log("tenant={}, seckill-published={}, failed={}, manual={}, overdue={}, trace={}",tenant,
                            result.getPublished(),result.getFailed(),result.getManual(),result.getOverdue(),trace);
                    return result.getFailed()==0 && result.getManual()==0 && result.getOverdue()==0;
                }
                if("REQUEST_RECONCILE".equals(code)) {
                    SeckillRequestReconcileService reconciliation=reconciliations.getIfAvailable();
                    if(reconciliation==null)throw new IllegalStateException("申请超时协调未启用");
                    SeckillRequestReconcileService.Result result=reconciliation.reconcile(5,trace);
                    XxlJobHelper.log("tenant={}, close-queued={}, skipped={}, failed={}, manual={}, trace={}",tenant,
                            result.getQueued(),result.getSkipped(),result.getFailed(),result.getManual(),trace);
                    return result.getFailed()==0 && result.getManual()==0;
                }
                if("INTENT_RECOVERY".equals(code)) {
                    SeckillIntentRecoveryService recovery=intentRecoveries.getIfAvailable();
                    if(recovery==null)throw new IllegalStateException("受理意图恢复未启用");
                    SeckillIntentRecoveryService.Result result=recovery.recover(5,trace);
                    XxlJobHelper.log("tenant={}, intent-synchronized={}, skipped={}, failed={}, manual={}, trace={}",tenant,
                            result.getSynchronizedCount(),result.getSkipped(),result.getFailed(),result.getManual(),trace);
                    return result.getFailed()==0 && result.getManual()==0;
                }
                if("ORDER_RESOURCE_SYNC".equals(code)) {
                    SeckillOrderReconcileService service=orderReconciliations.getIfAvailable();
                    if(service==null)throw new IllegalStateException("订单终局核对未启用");
                    SeckillOrderReconcileService.Result result=service.reconcile(5,trace);
                    XxlJobHelper.log("tenant={}, order-resource-completed={}, waiting={}, failed={}, manual={}, trace={}",tenant,
                            result.getCompleted(),result.getWaiting(),result.getFailed(),result.getManual(),trace);
                    return result.getFailed()==0 && result.getManual()==0;
                }
                if("ACTIVITY_EXPIRE".equals(code)) {
                    SeckillActivityExpiryService service=expiries.getIfAvailable();
                    if(service==null)throw new IllegalStateException("活动到期停止未启用");
                    SeckillCacheJobCursorDO position=write.execute(tx -> {
                        cursors.initializeCache(tenant);return cursors.lockCache(tenant);
                    });
                    if(position==null || position.getVersion()>=9007199254740991L)throw new IllegalStateException("原到期活动扫描位置需核对");
                    SeckillActivityExpiryService.Result result=service.expireBatch(5,position.getAfterExpiryActivity(),trace);
                    Integer updated=write.execute(tx -> cursors.advanceExpiry(tenant,position.getVersion(),result.getNextActivity()));
                    if(!Integer.valueOf(1).equals(updated))throw new IllegalStateException("原到期活动扫描位置已变化，下次按原义务恢复");
                    XxlJobHelper.log("tenant={}, expiry-attempted={}, synchronized={}, pending={}, trace={}",tenant,
                            result.getAttempted(),result.getSynchronizedCount(),result.getPending(),trace);
                    return result.getPending()==0;
                }
                if("POOL_RETURN_RECOVERY".equals(code)) {
                    SeckillPoolReturnService service=poolReturns.getIfAvailable();
                    SeckillClearingService clearing=clearings.getIfAvailable();
                    if(service==null || clearing==null)throw new IllegalStateException("闭场归还或清算未启用");
                    SeckillCacheJobCursorDO position=write.execute(tx -> {
                        cursors.initializeCache(tenant);return cursors.lockCache(tenant);
                    });
                    if(position==null || position.getVersion()>=9007199254740989L)throw new IllegalStateException("原归还扫描位置需核对");
                    // 原号商品查询/归还及Redis同步在游标短事务外执行，不创建新归还目标。
                    SeckillPoolReturnService.BatchResult result=service.recoverBatch(5,position.getAfterReturn(),trace);
                    Integer updated=write.execute(tx -> cursors.advancePoolReturn(tenant,position.getVersion(),result.getNextReturn()));
                    if(!Integer.valueOf(1).equals(updated))throw new IllegalStateException("原归还扫描位置已变化，下次按原进度恢复");
                    // 先恢复原号再准备新空闲池；新计划留给后续轮次，不在同一循环无限追进度。
                    SeckillPoolReturnService.PreparationResult prepared=service.prepareBatch(2,position.getAfterPoolId(),trace);
                    Integer preparationUpdated=write.execute(tx -> cursors.advancePoolPreparation(tenant,position.getVersion()+1,prepared.getNextPoolId()));
                    if(!Integer.valueOf(1).equals(preparationUpdated))throw new IllegalStateException("原池准备扫描位置已变化，下次按原记录恢复");
                    // 只推进一个原活动的一个清算阶段；游标短事务不包裹Redis或活动清算事务。
                    SeckillClearingService.BatchResult cleared=clearing.clearBatch(1,position.getAfterClearingActivity(),trace);
                    Integer clearingUpdated=write.execute(tx -> cursors.advanceClearing(tenant,position.getVersion()+2,cleared.getNextActivity()));
                    if(!Integer.valueOf(1).equals(clearingUpdated))throw new IllegalStateException("原清算扫描位置已变化，下次按原活动恢复");
                    XxlJobHelper.log("tenant={}, pool-return-attempted={}, completed={}, pending={}, prepared={}, prepare-pending={}, clearing-entered={}, clearing-completed={}, clearing-waiting={}, clearing-pending={}, trace={}",tenant,
                            result.getAttempted(),result.getCompleted(),result.getPending(),prepared.getPrepared(),prepared.getPending(),
                            cleared.getEntered(),cleared.getCompleted(),cleared.getWaiting(),cleared.getPending(),trace);
                    return result.getPending()==0 && prepared.getPending()==0 && cleared.getPending()==0;
                }
                if("ORDER_CACHE_SYNC".equals(code)) {
                    SeckillOrderCacheSyncService cache=orderCaches.getIfAvailable();
                    if(cache==null)throw new IllegalStateException("订单缓存恢复未启用");
                    SeckillCacheJobCursorDO position=write.execute(tx -> {
                        cursors.initializeCache(tenant);return cursors.lockCache(tenant);
                    });
                    if(position==null || position.getVersion()>=9007199254740991L)throw new IllegalStateException("原订单缓存扫描位置需核对");
                    // Redis在游标短事务结束后执行；部分失败仍保存处理到的原申请号。
                    SeckillOrderCacheSyncService.BatchResult result=cache.synchronizeBatch(5,position.getAfterRequest(),trace);
                    Integer updated=write.execute(tx -> cursors.advanceOrderCache(tenant,position.getVersion(),result.getNextRequest()));
                    if(!Integer.valueOf(1).equals(updated))throw new IllegalStateException("原订单缓存扫描位置已变化，下次按原进度恢复");
                    XxlJobHelper.log("tenant={}, order-cache-attempted={}, synchronized={}, pending={}, trace={}",tenant,
                            result.getAttempted(),result.getSynchronizedCount(),result.getPending(),trace);
                    return result.getPending()==0;
                }
                SeckillClosedCacheSyncService cache=caches.getIfAvailable();
                if(cache==null)throw new IllegalStateException("关闭缓存恢复未启用");
                SeckillCacheJobCursorDO position=write.execute(tx -> {
                    cursors.initializeCache(tenant);return cursors.lockCache(tenant);
                });
                if(position==null || position.getVersion()>=9007199254740991L)throw new IllegalStateException("原关闭缓存扫描位置需核对");
                // 短事务已结束，Redis调用不持有游标锁；并发重试由Lua幂等和游标版本CAS收敛。
                SeckillClosedCacheSyncService.BatchResult result=cache.synchronizeBatch(5,position.getAfterTime(),position.getAfterEvent(),trace);
                Integer updated=write.execute(tx -> cursors.advanceCache(tenant,position.getVersion(),result.getNextTime(),result.getNextEvent()));
                if(!Integer.valueOf(1).equals(updated))throw new IllegalStateException("原关闭缓存扫描位置已变化，下次按原进度恢复");
                XxlJobHelper.log("tenant={}, cache-attempted={}, synchronized={}, pending={}, trace={}",tenant,
                        result.getAttempted(),result.getSynchronizedCount(),result.getPending(),trace);
                return result.getPending()==0;
            });
        } catch(RuntimeException error) {
            XxlJobHelper.log("秒杀租户执行失败 tenant={}, code={}, trace={}",tenant,code,trace);return false;
        } finally {
            TenantContextHolder.clear();SecurityContextHolder.clearContext();
            MDC.remove("tenantId");MDC.remove("orderId");MDC.remove("eventId");MDC.remove("requestNo");MDC.remove("requestId");MDC.put("traceId",trace);
        }
    }
    private SeckillTenantJobCursorDO claim(String code,int shard,int total) {
        return write.execute(tx -> {
            LocalDateTime instant=now();cursors.initialize(code,shard,total,instant);
            SeckillTenantJobCursorDO row=cursors.lock(code,shard,total);
            if(row==null)throw new IllegalStateException("秒杀任务游标未确认");
            if(row.getLeaseUntil()!=null && row.getLeaseUntil().isAfter(instant))return null;
            long after=row.getLastTenantId(),ceiling=row.getCeilingTenantId();
            if(after>=ceiling) {
                Long maximum=cursors.ceiling(code);
                if(maximum==null || maximum<0 || maximum>9_007_199_254_740_991L)throw new IllegalStateException("秒杀租户边界不正确");
                after=0;ceiling=maximum;
            }
            String owner=UUID.randomUUID().toString();
            if(cursors.claim(code,shard,total,after,ceiling,owner,instant.plusSeconds(180),instant)!=1)
                throw new IllegalStateException("秒杀任务分片领取已变化");
            row.setLastTenantId(after);row.setCeilingTenantId(ceiling);row.setLeaseOwner(owner);return row;
        });
    }
    private void advance(SeckillTenantJobCursorDO row,Long tenant) {
        write.executeWithoutResult(tx -> {
            LocalDateTime instant=now();
            if(cursors.advance(row.getJobCode(),row.getShardIndex(),row.getShardTotal(),row.getLeaseOwner(),tenant,instant.plusSeconds(180),instant)!=1)
                throw new IllegalStateException("秒杀任务游标租约或进度已变化");
        });
        row.setLastTenantId(tenant);
    }
    private void validate(List<Long> tenants,SeckillTenantJobCursorDO row) {
        if(tenants==null || tenants.size()>5)throw new IllegalStateException("秒杀租户批次大小不正确");
        long previous=row.getLastTenantId();
        for(Long tenant:tenants) {
            if(tenant==null || tenant<=previous || tenant>row.getCeilingTenantId() || tenant%row.getShardTotal()!=row.getShardIndex())
                throw new IllegalStateException("秒杀租户游标结果不正确");
            previous=tenant;
        }
    }
    private LocalDateTime now(){return cursors.now();}
    private void clearLog(){MDC.remove("tenantId");MDC.remove("orderId");MDC.remove("eventId");MDC.remove("traceId");MDC.remove("requestNo");MDC.remove("requestId");}
}
