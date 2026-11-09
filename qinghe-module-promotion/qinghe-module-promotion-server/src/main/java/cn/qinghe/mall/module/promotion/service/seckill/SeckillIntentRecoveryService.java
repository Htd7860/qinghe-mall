package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillAdmissionRedisDAO;
import cn.qinghe.mall.module.promotion.framework.trade.SeckillOrderResultClient;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillQuoteBindingRespDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.util.*;

/** 原号恢复：先持久裁决，再事务外关闭trade/同步Redis，最后确认进度；不重新占额或重新受理。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-events",name="enabled",havingValue="true")
public class SeckillIntentRecoveryService {
    private final SeckillIntentRecoveryMapper records;
    private final SeckillAdmissionIntentMapper intents;
    private final SeckillActivityMapper activities;
    private final SeckillAdmissionMapper admissions;
    private final SeckillOrderResultMapper results;
    private final SeckillAdmissionRedisDAO redis;
    private final SeckillOrderResultClient trade;
    private final ObjectProvider<SeckillClosedCacheSyncService> closedCaches;
    private final ObjectProvider<SeckillOrderCacheSyncService> orderCaches;
    private final TransactionTemplate write;
    public SeckillIntentRecoveryService(SeckillIntentRecoveryMapper records,SeckillAdmissionIntentMapper intents,SeckillActivityMapper activities,
            SeckillAdmissionMapper admissions,SeckillOrderResultMapper results,SeckillAdmissionRedisDAO redis,SeckillOrderResultClient trade,
            ObjectProvider<SeckillClosedCacheSyncService> closedCaches,ObjectProvider<SeckillOrderCacheSyncService> orderCaches,PlatformTransactionManager manager) {
        this.records=records;this.intents=intents;this.activities=activities;this.admissions=admissions;this.results=results;
        this.redis=redis;this.trade=trade;this.closedCaches=closedCaches;
        this.orderCaches=orderCaches;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public Result recover(int batch,String trace) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("意图恢复只能由事务外可信后台调用");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || batch<1 || batch>20
                || trace==null || !trace.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw new IllegalArgumentException("意图恢复上下文不正确");
        List<SeckillAdmissionIntentDO> rows=records.due(tenant,batch);
        if (rows==null || rows.size()>batch) throw new IllegalStateException("原意图恢复批次不完整");
        Result out=new Result();long started=System.nanoTime();
        for (SeckillAdmissionIntentDO route:rows) {
            if (route==null || !Objects.equals(route.getTenantId(),tenant)) throw new IllegalStateException("原意图批次租户不一致");
            if (System.nanoTime()-started>=45000000000L) break;out.attempted++;
            SeckillAdmissionIntentDO decision=null;
            try {
                decision=write.execute(tx -> decide(tenant,route));
                if (decision==null) throw new IllegalStateException("原意图裁决未返回");
                if (Integer.valueOf(0).equals(decision.getStatus())) { out.skipped++;continue; }
                synchronize(decision);
                SeckillAdmissionIntentDO synchronizedDecision=decision;
                Integer done=write.execute(tx -> records.synced(synchronizedDecision));
                if (!Integer.valueOf(1).equals(done)) {
                    SeckillAdmissionIntentDO current=intents.get(tenant,route.getMemberId(),route.getClientRequestId());
                    if (current==null || !"SYNCED".equals(current.getCacheSyncState())) throw new IllegalStateException("原缓存同步回执尚未确认");
                }
                out.synchronizedCount++;
            } catch (RuntimeException pending) {
                out.failed++;SeckillAdmissionIntentDO observed=decision==null?route:decision;
                try { write.executeWithoutResult(tx -> records.defer(observed)); }
                catch (RuntimeException unavailable) { log.warn("Seckill intent retry metadata pending tenant={},request={},trace={}",tenant,route.getRequestNo(),trace); }
                log.warn("Seckill intent recovery pending tenant={},request={},trace={}",tenant,route.getRequestNo(),trace);
            }
        }
        out.manual=records.manual(tenant);
        if (out.manual>0) log.error("Seckill intents require original-decision reconciliation tenant={},manual={},trace={}",tenant,out.manual,trace);
        return out;
    }
    private SeckillAdmissionIntentDO decide(Long tenant,SeckillAdmissionIntentDO route) {
        // 只改变原意图，不写库存/个人额度；锁活动父行再锁意图，与受理的全局锁序一致。
        if (activities.lock(tenant,route.getActivityId())==null) throw new IllegalStateException("原意图活动父记录缺失");
        SeckillAdmissionIntentDO current=intents.lock(tenant,route.getRequestNo());
        if (current==null || !Objects.equals(current.getMemberId(),route.getMemberId()) || !Objects.equals(current.getContextDigest(),route.getContextDigest())) throw new IllegalStateException("原意图归属或摘要已变化");
        SeckillAdmissionIntentService.frozen(current);
        SeckillRequestDO accepted=admissions.ownRequest(tenant,current.getMemberId(),current.getRequestNo());
        if (Integer.valueOf(0).equals(current.getStatus())) {
            if (accepted!=null) throw new IllegalStateException("未裁决意图与实际受理申请冲突");
            if (admissions.now().isBefore(current.getQuoteExpiresAt())) return current;
            if (records.rejectExpired(current)!=1) throw new IllegalStateException("原过期意图拒绝裁决失败");
            return intents.lock(tenant,current.getRequestNo());
        }
        if (Integer.valueOf(20).equals(current.getStatus()) && accepted!=null
                || Integer.valueOf(10).equals(current.getStatus()) && accepted==null) throw new IllegalStateException("原裁决与实际受理事实冲突");
        return current;
    }
    private void synchronize(SeckillAdmissionIntentDO decision) {
        if ("SYNCED".equals(decision.getCacheSyncState())) return;
        if (Integer.valueOf(20).equals(decision.getStatus())) {
            SeckillQuoteBindingRespDTO closed=trade.closeRejected(decision);
            if (!Integer.valueOf(20).equals(closed.getState())) throw new IllegalStateException("拒绝申请已有真实订单，保留占用待核查");
            redis.releaseRejected(decision,closed);return;
        }
        if (!Integer.valueOf(10).equals(decision.getStatus())) throw new IllegalStateException("原意图仍未裁决");
        SeckillRequestDO accepted=admissions.ownRequest(decision.getTenantId(),decision.getMemberId(),decision.getRequestNo());
        if (accepted==null) throw new IllegalStateException("原持久受理申请缺失");
        if (Arrays.asList("PAID","CANCELLED").contains(accepted.getResourceState())) {
            SeckillOrderCacheSyncService cache=orderCaches.getIfAvailable();
            if (cache==null) throw new IllegalStateException("原订单终局缓存同步未启用");
            cache.synchronize(accepted.getRequestNo());return;
        }
        if (Integer.valueOf(50).equals(accepted.getStatus()) && accepted.getOrderId()==null) {
            String event=records.closedEvent(decision.getTenantId(),decision.getRequestNo());
            SeckillOrderResultDO receipt=event==null?null:results.receipt(decision.getTenantId(),event);
            if (receipt==null) throw new IllegalStateException("原受理关闭结果缺失");
            if (!"SYNCED".equals(receipt.getCacheSyncState())) {
                SeckillClosedCacheSyncService cache=closedCaches.getIfAvailable();
                if (cache==null) throw new IllegalStateException("原关闭缓存恢复未启用");cache.synchronize(event);
            }
            return;
        }
        redis.confirm(decision);
    }
    @Data public static class Result { private int attempted;private int skipped;private int synchronizedCount;private int failed;private long manual; }
}
