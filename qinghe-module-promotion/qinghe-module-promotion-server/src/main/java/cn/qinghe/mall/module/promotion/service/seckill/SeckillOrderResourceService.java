package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.trade.SeckillOrderResultClient;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillOrderStateDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.util.*;

/** 复用原库存条件增减/事务流水；付款累计限购不因退款减少。原作者HUIHUI的库存职责适配。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillOrderResourceService {
    private final SeckillAdmissionMapper admissions;
    private final SeckillActivityMapper activities;
    private final SeckillOrderResultMapper results;
    private final SeckillOrderResourceMapper resources;
    private final SeckillOrderResultClient trade;
    private final SeckillOrderCacheSyncService cache;
    private final TransactionTemplate write;
    public SeckillOrderResourceService(SeckillAdmissionMapper admissions,SeckillActivityMapper activities,
            SeckillOrderResultMapper results,SeckillOrderResourceMapper resources,SeckillOrderResultClient trade,
            SeckillOrderCacheSyncService cache,PlatformTransactionManager manager) {
        this.admissions=admissions;this.activities=activities;this.results=results;this.resources=resources;this.trade=trade;
        this.cache=cache;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    /** 供固定后台核对任务调用，无HTTP；仅已绑定真实订单可转换。false表示当前未终结，不是取消许可。 */
    public boolean synchronize(String requestNo) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("资源转换仅限事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || requestNo==null
                || !requestNo.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw new IllegalArgumentException("需要原租户申请号");
        SeckillRequestDO route=admissions.forDispatch(tenant,requestNo);
        if (route==null || route.getOrderId()==null) throw new IllegalStateException("原真实订单尚未绑定");
        SeckillRequestReceiptService.project(route);
        SeckillOrderStateDTO proof=trade.state(route); // 固定来源RPC只在锁/事务外。
        long checked=System.nanoTime();
        Boolean done=write.execute(tx -> apply(route,proof,checked));
        if (done==null) throw new IllegalStateException("原资源转换未返回结果");
        if (done) {
            try { cache.synchronize(requestNo); }
            catch (RuntimeException pending) { log.warn("Seckill resource transaction committed, cache pending tenant={},request={}",tenant,requestNo); }
        }
        return done; // true只确认数据库终局，缓存状态由持久字段单独表示。
    }
    private boolean apply(SeckillRequestDO route,SeckillOrderStateDTO proof,long checked) {
        if (!Objects.equals(route.getTenantId(),TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()) throw new IllegalStateException("资源转换租户变化");
        SeckillActivityDO activity=activities.lock(route.getTenantId(),route.getActivityId());
        if (activity==null || !Objects.equals(activity.getGeneration(),route.getGeneration())) throw new IllegalStateException("原活动代次已变化");
        SeckillActivitySkuDO pool=admissions.lockPool(route.getTenantId(),route.getActivityId(),route.getSkuId());
        SeckillMemberQuotaDO quota=admissions.lockQuota(route.getTenantId(),route.getActivityId(),route.getGeneration(),route.getMemberId());
        SeckillRequestDO r=results.lockRequest(route.getTenantId(),route.getRequestNo());
        if (r==null || pool==null || quota==null || !Objects.equals(r.getOrderId(),route.getOrderId())
                || !Objects.equals(r.getRequestDigest(),route.getRequestDigest()) || !Objects.equals(r.getActivityId(),route.getActivityId())
                || !Objects.equals(r.getGeneration(),route.getGeneration()) || !Objects.equals(r.getMemberId(),route.getMemberId())
                || !Objects.equals(r.getSkuId(),route.getSkuId()) || !Objects.equals(r.getQuantity(),route.getQuantity())) throw new IllegalStateException("原申请资源归属不一致");
        if (System.nanoTime()-checked>5000000000L) throw new IllegalStateException("原订单状态核对已过时，请按原号重查");
        boolean paid=Boolean.TRUE.equals(proof.getPayStatus());
        boolean cancelled=Boolean.FALSE.equals(proof.getPayStatus()) && Integer.valueOf(40).equals(proof.getOrderStatus());
        if (!paid && !cancelled) {
            if (!"HELD".equals(r.getResourceState())) throw new IllegalStateException("已终结资源对应的订单状态倒退，请核查原账");
            return false;
        }
        String state=paid?"PAID":"CANCELLED",type=paid?"ORDER_PAID":"ORDER_CANCELLED";
        // This ledger uses DATETIME(3). Project explicitly before both writing and repeat confirmation.
        LocalDateTime time=LocalDateTime.ofInstant((paid?proof.getPayTime():proof.getCancelTime()).toInstant(),ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (!"HELD".equals(r.getResourceState())) {
            if (!state.equals(r.getResourceState()) || !Objects.equals(r.getResourcePayOrderId(),proof.getPayOrderId())
                    || !time.equals(r.getResourceTime()) || r.getResourceOrderVersion()==null || proof.getOrderVersion()<r.getResourceOrderVersion()
                    || resources.journalExists(r,type,paid)!=1) throw new IllegalStateException("原订单终局资源转换冲突，请核查原账");
            return true; // 不再次改池、不回减paid、不重置缓存PENDING。
        }
        if (!Objects.equals(r.getVersion(),route.getVersion()) || !Arrays.asList(20,30,60).contains(r.getStatus())
                || pool.getHeldQuantity()<r.getQuantity() || quota.getHeldQuantity()<r.getQuantity()) throw new IllegalStateException("原占用或申请版本变化");
        if (resources.pool(r,pool.getVersion(),paid)!=1 || resources.member(r,quota.getVersion(),paid)!=1
                || resources.complete(r,state,proof.getOrderVersion(),proof.getPayOrderId(),time)!=1 || resources.journal(r,type,paid,proof.getOrderVersion())!=1)
            throw new IllegalStateException("原库存、个人额度、申请终局与流水未完整转换");
        return true;
    }
}
