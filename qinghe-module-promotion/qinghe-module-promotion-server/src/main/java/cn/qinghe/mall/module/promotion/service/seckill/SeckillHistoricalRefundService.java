package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.product.SeckillStockClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.util.*;

/** 原清算基线下的历史退款：固定累计目标→事务外原商品结果→实际回执/退款/流水同事务，不回补新池。 */
@Service
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillHistoricalRefundService {
    private final SeckillActivityMapper activities;
    private final SeckillOrderResultMapper requests;
    private final SeckillOrderResourceMapper resources;
    private final SeckillRefundRecordMapper refunds;
    private final SeckillHistoricalRefundMapper history;
    private final SeckillPoolReturnMapper returns;
    private final SeckillStockResultMapper receipts;
    private final SeckillStockClient stock;
    private final TransactionTemplate write;
    public SeckillHistoricalRefundService(SeckillActivityMapper activities,SeckillOrderResultMapper requests,SeckillOrderResourceMapper resources,
            SeckillRefundRecordMapper refunds,SeckillHistoricalRefundMapper history,SeckillPoolReturnMapper returns,
            SeckillStockResultMapper receipts,SeckillStockClient stock,PlatformTransactionManager manager) {
        this.activities=activities;this.requests=requests;this.resources=resources;this.refunds=refunds;this.history=history;this.returns=returns;this.receipts=receipts;
        this.stock=stock;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    /** 售后号为稳定业务身份，数量和原划拨均从已确认本库事实取得，无自由库存输入。 */
    public SeckillPoolReturnDO prepare(Long afterSaleId) {
        SeckillRefundCommandContext.requireCaller();Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || afterSaleId==null || afterSaleId<=0 || afterSaleId>9007199254740991L)
            throw new IllegalArgumentException("需要可信原租户及售后号");
        SeckillRefundRecordDO route=refunds.get(tenant,afterSaleId);
        if(route==null)throw new IllegalStateException("原成功退款尚未登记");
        return write.execute(tx->{
            if(!Objects.equals(TenantContextHolder.getTenantId(),tenant) || TenantContextHolder.isIgnore())throw new IllegalStateException("历史退款租户变化");
            SeckillActivityDO current=activities.lock(tenant,route.getActivityId());
            if(!historicalStage(tenant,current,route.getGeneration()))
                throw new IllegalStateException("原代次尚未进入清算，不能绕过当前池恢复");
            SeckillClearingPoolDO baseline=history.baseline(tenant,route.getActivityId(),route.getGeneration(),route.getSkuId());
            if(baseline==null)throw new IllegalStateException("原不可变清算基线及审计缺失");
            SeckillRequestDO request=requests.lockRequest(tenant,route.getRequestNo());
            SeckillRefundRecordDO refund=refunds.lock(tenant,afterSaleId);
            if(refund==null || request==null || !Objects.equals(refund.getRequestNo(),route.getRequestNo()) || !Objects.equals(refund.getOrderId(),request.getOrderId())
                    || !Objects.equals(refund.getMemberId(),request.getMemberId()) || !Objects.equals(refund.getActivityId(),request.getActivityId())
                    || !Objects.equals(refund.getGeneration(),request.getGeneration()) || !Objects.equals(refund.getRedisEpoch(),request.getRedisEpoch())
                    || !Objects.equals(refund.getSkuId(),request.getSkuId()) || !"PAID".equals(request.getResourceState())
                    || resources.journalExists(request,"ORDER_PAID",true)!=1 || !Objects.equals(baseline.getRedisEpoch(),refund.getRedisEpoch()))
                throw new IllegalStateException("历史退款与原付款/清算归属不一致");
            SeckillPoolReturnDO previous=history.plan(tenant,afterSaleId);
            if(previous!=null) {
                if(!Objects.equals(previous.getActivityId(),refund.getActivityId()) || !Objects.equals(previous.getGeneration(),refund.getGeneration())
                        || !Objects.equals(previous.getSkuId(),refund.getSkuId()) || !Objects.equals(previous.getQuantity(),refund.getRestoreCount())
                        || !Objects.equals(previous.getAllocationOperationNo(),baseline.getAllocationOperationNo()))throw new IllegalStateException("原历史归还号归属变化");
                return previous; // 原固定目标不可改，重复确认仍须实际回执/流水。
            }
            if("NOT_REQUIRED".equals(refund.getState()) || "RESTORED".equals(refund.getState()))return null;
            if(!"PENDING".equals(refund.getState()) || refund.getRestoreCount()<=0)throw new IllegalStateException("原退款不支持历史库存归还");
            if(returns.pending(tenant,refund.getActivityId(),refund.getGeneration(),refund.getSkuId())!=null)
                throw new IllegalStateException("原规格已有待确认归还，请先恢复原号");
            SeckillHistoricalRefundMapper.Progress progress=history.progress(tenant,refund.getActivityId(),refund.getGeneration(),refund.getSkuId());
            SeckillStockReceiptDO latest=receipts.latest(tenant,refund.getActivityId(),refund.getGeneration(),refund.getSkuId());
            if(progress==null || progress.getReturnedCount()==null || progress.getConfirmedPlans()==null || progress.getReturnedCount()<0 || progress.getConfirmedPlans()<0
                    || progress.getReturnedCount()+refund.getRestoreCount()>baseline.getSoldQuantity() || latest==null
                    || !Objects.equals(latest.getAllocatedQuantity(),baseline.getAllocatedQuantity())
                    || latest.getReturnedQuantity().longValue()!=baseline.getReturnedQuantity()+progress.getReturnedCount()
                    || latest.getProductVersion()!=baseline.getProductVersion()+progress.getConfirmedPlans() || latest.getProductVersion()>=9007199254740991L
                    || !Integer.valueOf(latest.getReturnedQuantity()==0?10:latest.getReturnedQuantity().equals(latest.getAllocatedQuantity())?30:50).equals(latest.getProductStatus()))
                throw new IllegalStateException("原历史归还累计流水与实际商品回执不一致");
            Long restored=refunds.restoredTotal(tenant,refund.getRequestNo());
            if(restored==null || restored<0 || restored+refund.getRestoreCount()>request.getQuantity())throw new IllegalStateException("原单累计恢复超出购买量");
            SeckillPoolReturnDO plan=new SeckillPoolReturnDO();plan.setTenantId(tenant);plan.setReturnNo(UUID.randomUUID().toString());
            plan.setActivityId(refund.getActivityId());plan.setGeneration(refund.getGeneration());plan.setRedisEpoch(refund.getRedisEpoch());plan.setSkuId(refund.getSkuId());
            plan.setAllocationOperationNo(baseline.getAllocationOperationNo());plan.setAllocationSnapshot(baseline.getAllocationSnapshot());plan.setQuantity(refund.getRestoreCount());
            plan.setBaseReturned(latest.getReturnedQuantity());plan.setTargetReturned(Math.addExact(latest.getReturnedQuantity(),refund.getRestoreCount()));
            plan.setBaseProductVersion(latest.getProductVersion());plan.setSourceKind("HISTORICAL_REFUND");plan.setAfterSaleId(afterSaleId);
            if(plan.getTargetReturned()>baseline.getAllocatedQuantity() || history.insert(plan)!=1)throw new IllegalStateException("原历史归还计划尚未持久保存");
            SeckillPoolReturnDO saved=history.plan(tenant,afterSaleId);
            if(saved==null)throw new IllegalStateException("原历史归还提交结果待确认，请按原售后号查询");return saved;
        });
    }
    public boolean hasBaseline(SeckillRefundRecordDO refund) {
        Long tenant=context();
        return refund!=null && Objects.equals(refund.getTenantId(),tenant)
                && history.baseline(tenant,refund.getActivityId(),refund.getGeneration(),refund.getSkuId())!=null;
    }
    public boolean hasPlan(Long afterSaleId) {Long tenant=context();return history.plan(tenant,afterSaleId)!=null;}
    public void synchronize(Long afterSaleId) {
        Long tenant=context();
        if(!confirmed(tenant,afterSaleId))throw new IllegalStateException("原历史退款实际归还仍待确认");
        // 原代次已有不可变清算证明，运行缓存不再参与准入；没有新池或旧缓存回补。
    }
    public boolean recover(Long afterSaleId) {
        Long tenant=context();SeckillPoolReturnDO plan=prepare(afterSaleId);
        if(plan==null) {
            SeckillRefundRecordDO f=refunds.get(tenant,afterSaleId);
            if(f!=null && ("NOT_REQUIRED".equals(f.getState()) && f.getRestoreCount()==0 || "RESTORED".equals(f.getState()) && refunds.restoredProof(tenant,afterSaleId)!=null))return true;
            throw new IllegalStateException("原历史退款没有待恢复计划或实际恢复证明");
        }
        if("CONFIRMED".equals(plan.getState()))return requireConfirmed(tenant,afterSaleId);
        if(!"PENDING".equals(plan.getState()))throw new IllegalStateException("原历史归还状态不支持自动推进");
        ProductSeckillAllocationRespDTO response=stock.historicalRefundResult(afterSaleId);
        SeckillPoolReturnDO observed=history.plan(tenant,afterSaleId);
        if(observed!=null && "CONFIRMED".equals(observed.getState()))return requireConfirmed(tenant,afterSaleId);
        if(response==null)throw new IllegalStateException("原商品划拨结果缺失，保留历史归还计划待核查");
        if(Objects.equals(response.getReturnedQuantity(),plan.getBaseReturned())) {
            if(!Objects.equals(response.getVersion(),plan.getBaseProductVersion()) || !Arrays.asList(10,50).contains(response.getStatus()))
                throw new IllegalStateException("原历史归还商品基线变化");
            response=stock.returnHistoricalRefund(afterSaleId);
        }
        ProductSeckillAllocationRespDTO actual=response;
        if(!Objects.equals(actual.getReturnedQuantity(),plan.getTargetReturned()) || actual.getVersion()!=plan.getBaseProductVersion()+1
                || !Integer.valueOf(plan.getTargetReturned().equals(actual.getAllocatedQuantity())?30:50).equals(actual.getStatus()))
            throw new IllegalStateException("商品原累计归还目标或版本待核查，不能覆盖原计划");
        Boolean done=write.execute(tx->{
            if(!Objects.equals(TenantContextHolder.getTenantId(),tenant) || TenantContextHolder.isIgnore())throw new IllegalStateException("历史归还确认租户变化");
            SeckillActivityDO activity=activities.lock(tenant,plan.getActivityId());
            if(!historicalStage(tenant,activity,plan.getGeneration()))
                throw new IllegalStateException("原清算阶段尚未确认，不能修改当前活动池");
            SeckillClearingPoolDO baseline=history.baseline(tenant,plan.getActivityId(),plan.getGeneration(),plan.getSkuId());
            SeckillRefundRecordDO route=refunds.get(tenant,afterSaleId);
            if(baseline==null || route==null)throw new IllegalStateException("原清算或成功退款证明缺失");
            SeckillRequestDO request=requests.lockRequest(tenant,route.getRequestNo());
            SeckillRefundRecordDO refund=refunds.lock(tenant,afterSaleId);
            SeckillPoolReturnDO current=returns.lock(tenant,plan.getReturnNo());
            if(current!=null && "CONFIRMED".equals(current.getState()))return requireConfirmed(tenant,afterSaleId);
            if(current==null || !"HISTORICAL_REFUND".equals(current.getSourceKind()) || !"PENDING".equals(current.getState())
                    || !Objects.equals(current.getAfterSaleId(),afterSaleId) || !Objects.equals(current.getVersion(),plan.getVersion())
                    || !Objects.equals(current.getTargetReturned(),plan.getTargetReturned()) || !Objects.equals(current.getBaseProductVersion(),plan.getBaseProductVersion())
                    || !Objects.equals(current.getBaseReturned(),plan.getBaseReturned()) || !Objects.equals(current.getAllocationSnapshot(),baseline.getAllocationSnapshot())
                    || !Objects.equals(current.getAllocationOperationNo(),baseline.getAllocationOperationNo()) || !Objects.equals(current.getRedisEpoch(),baseline.getRedisEpoch())
                    || refund==null || request==null || !"PENDING".equals(refund.getState()) || !Objects.equals(refund.getRestoreCount(),current.getQuantity())
                    || !Objects.equals(refund.getRequestNo(),request.getRequestNo())
                    || !Objects.equals(refund.getActivityId(),current.getActivityId()) || !Objects.equals(refund.getGeneration(),current.getGeneration())
                    || !Objects.equals(refund.getSkuId(),current.getSkuId()) || !Objects.equals(refund.getRedisEpoch(),current.getRedisEpoch())
                    || !Objects.equals(refund.getOrderId(),request.getOrderId()) || !Objects.equals(refund.getMemberId(),request.getMemberId())
                    || !Objects.equals(request.getActivityId(),current.getActivityId()) || !Objects.equals(request.getGeneration(),current.getGeneration())
                    || !Objects.equals(request.getSkuId(),current.getSkuId()) || !Objects.equals(request.getRedisEpoch(),current.getRedisEpoch())
                    || !"PAID".equals(request.getResourceState()) || resources.journalExists(request,"ORDER_PAID",true)!=1)
                throw new IllegalStateException("商品已归还，但原售后/计划/付款/清算证明待确认");
            SeckillStockReceiptDO latest=receipts.latest(tenant,current.getActivityId(),current.getGeneration(),current.getSkuId());
            SeckillHistoricalRefundMapper.Progress progress=history.progress(tenant,current.getActivityId(),current.getGeneration(),current.getSkuId());
            if(latest==null || !Objects.equals(latest.getProductVersion(),current.getBaseProductVersion()) || !Objects.equals(latest.getReturnedQuantity(),current.getBaseReturned())
                    || !Objects.equals(actual.getAllocatedQuantity(),baseline.getAllocatedQuantity()) || progress==null
                    || progress.getReturnedCount()+refund.getRestoreCount()>baseline.getSoldQuantity()
                    || current.getBaseReturned().longValue()!=baseline.getReturnedQuantity()+progress.getReturnedCount()
                    || current.getBaseProductVersion()!=baseline.getProductVersion()+progress.getConfirmedPlans())throw new IllegalStateException("原历史累计恢复基线不一致");
            Long total=refunds.restoredTotal(tenant,refund.getRequestNo());
            if(total==null || total<0 || total+refund.getRestoreCount()>request.getQuantity())throw new IllegalStateException("原单累计恢复超限");
            SeckillStockReceiptDO receipt=new SeckillStockReceiptDO();receipt.setTenantId(tenant);receipt.setActivityId(current.getActivityId());
            receipt.setGeneration(current.getGeneration());receipt.setSkuId(current.getSkuId());receipt.setProductVersion(actual.getVersion());receipt.setProductStatus(actual.getStatus());
            receipt.setAllocatedQuantity(actual.getAllocatedQuantity());receipt.setReturnedQuantity(actual.getReturnedQuantity());
            if(receipts.insert(receipt)!=1 || history.confirmed(current,actual.getVersion(),actual.getStatus())!=1
                    || history.restored(refund,(int)(total+refund.getRestoreCount()))!=1 || history.journal(refund)!=1)
                throw new IllegalStateException("原商品回执/归还计划/退款恢复/流水未完整保存");
            return requireConfirmed(tenant,afterSaleId);
        });
        if(!Boolean.TRUE.equals(done))throw new IllegalStateException("原历史归还确认结果待查询");return true;
    }
    private boolean historicalStage(Long tenant,SeckillActivityDO activity,Integer generation) {
        if(activity==null || generation==null || activity.getGeneration()==null || activity.getGeneration()<generation)return false;
        if(activity.getGeneration()>generation || Arrays.asList(40,50).contains(activity.getStatus()))return true;
        return Integer.valueOf(0).equals(activity.getStatus()) && activity.getPublishOperationNo()==null
                && Long.valueOf(0).equals(activity.getRedisEpoch()) && Long.valueOf(1).equals(history.reopened(tenant,activity.getId(),generation));
    }
    private boolean confirmed(Long tenant,Long sale) {return history.restoredProof(tenant,sale)!=null;}
    private boolean requireConfirmed(Long tenant,Long sale) {
        if(!confirmed(tenant,sale))throw new IllegalStateException("原历史退款缺少实际商品回执/清算审计/恢复流水");return true;
    }
    private Long context() {
        SeckillRefundCommandContext.requireCaller();Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore())throw new IllegalStateException("需要可信原租户");return tenant;
    }
}
