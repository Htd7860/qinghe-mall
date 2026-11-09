package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
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

/** 原超时扫描→业务委托职责。只排入原关闭事件，不直接推断trade未建单或释放任何资源。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-events",name="enabled",havingValue="true")
public class SeckillRequestReconcileService {
    private final SeckillRequestReconcileMapper records;
    private final SeckillActivityMapper activities;
    private final SeckillAdmissionMapper admissions;
    private final SeckillAdmissionIntentMapper intents;
    private final SeckillOrderResultMapper requests;
    private final TransactionTemplate write;
    public SeckillRequestReconcileService(SeckillRequestReconcileMapper records,SeckillActivityMapper activities,SeckillAdmissionMapper admissions,
            SeckillAdmissionIntentMapper intents,SeckillOrderResultMapper requests,PlatformTransactionManager manager) {
        this.records=records;this.activities=activities;this.admissions=admissions;this.intents=intents;this.requests=requests;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public Result reconcile(int batch,String trace) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("申请超时协调只能由事务外可信后台调用");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || batch<1 || batch>20
                || trace==null || !trace.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("超时协调租户、批次或trace不正确");
        List<SeckillRequestDO> due=records.due(tenant,batch);
        if (due==null || due.size()>batch) throw new IllegalStateException("原到期申请批次不完整");
        Result result=new Result();long started=System.nanoTime();
        for (SeckillRequestDO route:due) {
            if (route==null || !Objects.equals(route.getTenantId(),tenant)) throw new IllegalStateException("原到期申请批次租户不正确");
            if (System.nanoTime()-started>=45000000000L) break;result.attempted++;
            try {
                Boolean queued=write.execute(tx -> queue(tenant,route));
                if (Boolean.TRUE.equals(queued)) result.queued++;else result.skipped++;
            } catch (RuntimeException pending) {
                result.failed++;
                // 原事务提交响应未知时，若实际已进30，defer的状态/版本条件不匹配，不会改写原事件或退资源。
                try { write.executeWithoutResult(tx -> records.defer(route)); }
                catch (RuntimeException unavailable) { log.warn("Seckill timeout retry metadata pending tenant={},request={},trace={}",tenant,route.getRequestNo(),trace); }
                log.warn("Seckill timeout close enqueue pending tenant={},request={},trace={}",tenant,route.getRequestNo(),trace);
            }
        }
        result.manual=records.manual(tenant);
        if (result.manual>0) log.error("Seckill timeout requests require reconciliation tenant={},manual={},trace={}",tenant,result.manual,trace);
        return result;
    }
    private boolean queue(Long tenant,SeckillRequestDO route) {
        if (route==null || !Objects.equals(route.getTenantId(),tenant)) throw new IllegalStateException("到期申请租户不一致");
        SeckillActivityDO activity=activities.lock(tenant,route.getActivityId());
        if (activity==null || !Objects.equals(activity.getGeneration(),route.getGeneration())) throw new IllegalStateException("原活动代次待核对");
        if (admissions.lockPool(tenant,route.getActivityId(),route.getSkuId())==null
                || admissions.lockQuota(tenant,route.getActivityId(),route.getGeneration(),route.getMemberId())==null) throw new IllegalStateException("原占用账本缺失");
        SeckillRequestDO r=requests.lockRequest(tenant,route.getRequestNo());
        if (r==null || !Objects.equals(r.getActivityId(),route.getActivityId()) || !Objects.equals(r.getGeneration(),route.getGeneration())
                || !Objects.equals(r.getMemberId(),route.getMemberId()) || !Objects.equals(r.getSkuId(),route.getSkuId())) throw new IllegalStateException("原申请路由不一致");
        SeckillRequestReceiptService.project(r);LocalDateTime now=admissions.now();
        if (!Arrays.asList(10,60).contains(r.getStatus()) || r.getOrderId()!=null || r.getRetryCount()>=10
                || now.isBefore(r.getReconcileDeadline()) || now.isBefore(r.getNextRetryTime())
                || r.getLockedUntil()!=null && now.isBefore(r.getLockedUntil())) return false;
        SeckillAdmissionIntentDO intent=intents.get(tenant,r.getMemberId(),r.getClientRequestId());
        if (intent==null || !Integer.valueOf(10).equals(intent.getStatus()) || !Objects.equals(intent.getRequestNo(),r.getRequestNo())
                || !Objects.equals(intent.getRequestDigest(),r.getRequestDigest())) throw new IllegalStateException("原申请受理事实不完整");
        SeckillOutboxDO event=new SeckillOutboxDO();event.setTenantId(tenant);event.setEventId(UUID.randomUUID().toString());
        event.setRequestNo(r.getRequestNo());event.setEventType("SECKILL_CLOSE");event.setAggregateVersion(Math.addExact(r.getVersion(),1L));
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("schemaVersion",1);payload.put("tenantId",tenant);
        payload.put("requestNo",r.getRequestNo());payload.put("aggregateVersion",event.getAggregateVersion());event.setPayload(JsonUtils.toJsonString(payload));
        if (records.startClose(r)!=1 || records.insertClose(event)!=1) throw new IllegalStateException("原申请关闭意图及事件未完整写入");
        return true;
    }
    @Data public static class Result { private int attempted;private int queued;private int skipped;private int failed;private long manual; }
}
