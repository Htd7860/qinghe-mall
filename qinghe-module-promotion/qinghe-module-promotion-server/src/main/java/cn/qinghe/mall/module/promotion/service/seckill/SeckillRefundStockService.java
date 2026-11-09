package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillAdmissionRedisDAO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.util.Arrays;
import java.util.Objects;

/** 原售后恢复职责：先本地池/原退款/流水同事务，再事务外同步Redis；累计已购次数保持。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillRefundStockService {
    private final SeckillActivityMapper activities;
    private final SeckillAdmissionMapper admissions;
    private final SeckillOrderResultMapper requests;
    private final SeckillOrderResourceMapper resources;
    private final SeckillRefundRecordMapper refunds;
    private final SeckillOrderCacheSyncService orderCache;
    private final SeckillAdmissionRedisDAO redis;
    private final SeckillHistoricalRefundService historical;
    private final TransactionTemplate write;
    public SeckillRefundStockService(SeckillActivityMapper activities,SeckillAdmissionMapper admissions,
            SeckillOrderResultMapper requests,SeckillOrderResourceMapper resources,SeckillRefundRecordMapper refunds,
            SeckillOrderCacheSyncService orderCache,SeckillAdmissionRedisDAO redis,SeckillHistoricalRefundService historical,PlatformTransactionManager manager) {
        this.activities=activities;this.admissions=admissions;this.requests=requests;this.resources=resources;
        this.refunds=refunds;this.orderCache=orderCache;this.redis=redis;
        this.historical=historical;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** true仅确认数据库已恢复；缓存进度单独持久化，失败仍可按原号重试。 */
    public boolean restore(Long afterSaleId) {
        Long tenant=context(afterSaleId);
        SeckillRefundRecordDO route=refunds.get(tenant,afterSaleId);
        if(route==null)throw new IllegalStateException("原成功退款尚未登记");
        if(historical.hasBaseline(route))return historical.recover(afterSaleId);
        Boolean done=write.execute(tx -> {
            if(!Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())
                throw new IllegalStateException("退款恢复租户变化");
            SeckillActivityDO activity=activities.lock(tenant,route.getActivityId());
            if(activity==null)throw new IllegalStateException("原活动缺失，请核查");
            // 固定锁顺序：活动→当前原代次池→申请→原退款；新代次池绝不用于旧退款。
            boolean current=Objects.equals(activity.getGeneration(),route.getGeneration());
            SeckillActivitySkuDO pool=current?admissions.lockPool(tenant,route.getActivityId(),route.getSkuId()):null;
            SeckillRequestDO r=requests.lockRequest(tenant,route.getRequestNo());
            SeckillRefundRecordDO f=refunds.lock(tenant,afterSaleId);
            if(f==null || r==null || !Objects.equals(f.getRequestNo(),route.getRequestNo())
                    || !Objects.equals(f.getActivityId(),r.getActivityId()) || !Objects.equals(f.getGeneration(),r.getGeneration())
                    || !Objects.equals(f.getRedisEpoch(),r.getRedisEpoch()) || !Objects.equals(f.getSkuId(),r.getSkuId())
                    || !Objects.equals(f.getOrderId(),r.getOrderId()) || !Objects.equals(f.getMemberId(),r.getMemberId())
                    || !"PAID".equals(r.getResourceState()) || resources.journalExists(r,"ORDER_PAID",true)!=1)
                throw new IllegalStateException("原退款与付款资源归属不一致");
            if("NOT_REQUIRED".equals(f.getState()))return true;
            if("RESTORED".equals(f.getState())) {
                if(refunds.restoredProof(tenant,afterSaleId)==null)throw new IllegalStateException("原退款恢复流水缺失");
                return true; // 不能再次回补，也不重置缓存同步结果。
            }
            if(!"PENDING".equals(f.getState()) || f.getRestoreCount()<=0 || !current || pool==null
                    || !Objects.equals(activity.getRedisEpoch(),f.getRedisEpoch()) || !Arrays.asList(20,30).contains(activity.getStatus())
                    || pool.getSoldQuantity()<f.getRestoreCount() || !Integer.valueOf(20).equals(pool.getAllocationStatus()))
                throw new IllegalStateException("原代次已进入其他清算阶段，保留退款义务待核对");
            Long total=refunds.restoredTotal(tenant,f.getRequestNo());
            if(total==null || total<0 || total+f.getRestoreCount()>r.getQuantity())throw new IllegalStateException("原单累计回补数量不正确");
            if(refunds.restorePool(f,pool.getVersion())!=1 || refunds.restored(f,(int)(total+f.getRestoreCount()))!=1 || refunds.journal(f)!=1)
                throw new IllegalStateException("原退款库存、恢复进度与流水未完整提交");
            return true;
        });
        if(!Boolean.TRUE.equals(done))throw new IllegalStateException("原退款恢复结果未确认");
        try { synchronize(afterSaleId); }
        catch(RuntimeException pending){log.warn("Seckill refund stock committed, cache pending tenant={},sale={}",tenant,afterSaleId);}
        return true;
    }

    public void synchronize(Long afterSaleId) {
        Long tenant=context(afterSaleId);
        if(historical.hasPlan(afterSaleId)) {historical.synchronize(afterSaleId);return;}
        SeckillRefundRecordDO proof=refunds.restoredProof(tenant,afterSaleId);
        if(proof==null) {
            SeckillRefundRecordDO empty=refunds.get(tenant,afterSaleId);
            if(empty!=null && "NOT_REQUIRED".equals(empty.getState()) && empty.getRestoreCount()==0)return;
            throw new IllegalStateException("原退款恢复事实及流水未确认");
        }
        if("SYNCED".equals(proof.getCacheState()))return;
        orderCache.synchronize(proof.getRequestNo()); // 先确保原支付P转换，后应用累计退款差额。
        redis.synchronizeRefund(afterSaleId);
        if(!Objects.equals(context(afterSaleId),tenant))throw new IllegalStateException("退款缓存同步租户变化");
        Integer ack=write.execute(tx -> refunds.cacheSynced(proof));
        if(Integer.valueOf(1).equals(ack))return;
        SeckillRefundRecordDO current=refunds.restoredProof(tenant,afterSaleId);
        if(current!=null && "SYNCED".equals(current.getCacheState())
                && Objects.equals(current.getRestoredTotal(),proof.getRestoredTotal()))return;
        throw new IllegalStateException("原退款缓存转换已执行，本库同步进度待确认");
    }

    private Long context(Long sale) {
        SeckillRefundCommandContext.requireCaller();
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || sale==null || sale<=0 || sale>9007199254740991L)throw new IllegalArgumentException("需要原租户售后号");
        return tenant;
    }
}
