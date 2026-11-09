package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillInitialRuntimeRedisDAO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import javax.validation.Validator;
import java.util.*;

/** 原停止阶段进入清算：空闲/占用已处理，原已售余额保留为历史退款基线，不提前宣布资金结清。 */
@Service
@Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillClearingService {
    private final SeckillActivityMapper activities;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillPublishMapper publishes;
    private final SeckillPublishDecisionMapper decisions;
    private final SeckillClearingMapper clearing;
    private final SeckillHistoricalRefundMapper history;
    private final SeckillInitialRuntimeRedisDAO runtime;
    private final Validator validator;
    private final TransactionTemplate write;
    public SeckillClearingService(SeckillActivityMapper activities,SeckillRedisEpochMapper epochs,SeckillPublishMapper publishes,
            SeckillPublishDecisionMapper decisions,SeckillClearingMapper clearing,SeckillHistoricalRefundMapper history,SeckillInitialRuntimeRedisDAO runtime,
            Validator validator,PlatformTransactionManager manager) {
        this.activities=activities;this.epochs=epochs;this.publishes=publishes;this.decisions=decisions;this.clearing=clearing;this.runtime=runtime;this.validator=validator;
        this.history=history;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(15);
    }
    /** 固定归还恢复任务调用；原generation必填，不把迟到旧请求绑定到新池。 */
    public List<SeckillClearingPoolDO> beginClearing(Long activityId,Integer generation) {
        Long tenant=context();if(!id(activityId) || generation==null || generation<1)throw new IllegalArgumentException("需要原活动及发布代次");
        SeckillActivityDO before=activities.get(tenant,activityId);
        if(before==null || !Objects.equals(before.getGeneration(),generation))throw new IllegalStateException("原活动代次已变化");
        SeckillRedisEpochDO stopped=epochs.initial(tenant,activityId,generation);
        if(!closed(stopped) || !Objects.equals(stopped.getPublishRequestId(),before.getPublishOperationNo()))throw new IllegalStateException("原停止或缓存关闭尚未确认");
        if(Integer.valueOf(40).equals(before.getStatus()))return sealed(tenant,activityId,generation,stopped);
        if(!Integer.valueOf(30).equals(before.getStatus()))throw new IllegalStateException("只有原停止活动可进入清算");
        runtime.close(stopped); // 原不可逆关闭/未运行证明在父锁事务外核对。
        List<SeckillClearingPoolDO> saved=write.execute(tx->{
            if(!Objects.equals(contextTenant(),tenant))throw new IllegalStateException("清算租户变化");
            SeckillActivityDO activity=activities.lock(tenant,activityId);
            if(activity==null || !Objects.equals(activity.getGeneration(),generation) || !Objects.equals(activity.getPublishOperationNo(),stopped.getPublishRequestId()))
                throw new IllegalStateException("原清算代次变化，不能复制新池");
            if(Integer.valueOf(40).equals(activity.getStatus()))return sealed(tenant,activityId,generation,stopped);
            SeckillRedisEpochDO epoch=epochs.initial(tenant,activityId,generation);
            if(!Integer.valueOf(30).equals(activity.getStatus()) || !Long.valueOf(1).equals(activity.getRedisEpoch()) || !closed(epoch)
                    || !Objects.equals(epoch.getDefinitionDigest(),stopped.getDefinitionDigest()) || !Objects.equals(epoch.getInitToken(),stopped.getInitToken()))
                throw new IllegalStateException("原清算关闭凭证变化");
            if(!Long.valueOf(0).equals(clearing.pendingObligations(tenant,activityId,generation)) || clearing.paidQuotaMatches(tenant,activityId,generation)!=1)
                throw new IllegalStateException("原订单/消息/退款/归还或会员额度尚待处理，不能进入清算");
            SeckillPublishRecordDO original=publishes.get(tenant,activity.getPublishOperationNo());
            if(original==null || !Objects.equals(original.getGeneration(),generation) || !Objects.equals(original.getActivityId(),activityId)
                    || !Long.valueOf(1).equals(decisions.completed(tenant,activityId,"PUBLISH",original.getRequestId()))
                    || !Long.valueOf(0).equals(decisions.completed(tenant,activityId,"WITHDRAW",original.getRequestId())))throw new IllegalStateException("原发布事实不完整");
            List<SeckillActivitySkuDO> pools=clearing.lockPools(tenant,activityId);
            List<SeckillStockReceiptDO> receipts=decisions.latestAll(tenant,activityId,generation);
            List<SeckillClearingMapper.LedgerBalance> balances=clearing.ledger(tenant,activityId,generation);
            ProductSeckillAllocationReqDTO[] requests=JsonUtils.parseObject(original.getRequestSnapshot(),ProductSeckillAllocationReqDTO[].class);
            if(pools==null || pools.isEmpty() || pools.size()>200 || receipts==null || balances==null || requests==null
                    || pools.size()!=receipts.size() || pools.size()!=balances.size() || pools.size()!=requests.length)
                throw new IllegalStateException("原清算规格/商品回执/额度流水不完整");
            Map<Long,ProductSeckillAllocationReqDTO> bySku=new HashMap<>();Set<String> operations=new HashSet<>();
            for(ProductSeckillAllocationReqDTO request:requests) {
                if(request==null || !validator.validate(request).isEmpty() || bySku.put(request.getSkuId(),request)!=null || !operations.add(request.getOperationNo()))
                    throw new IllegalStateException("原划拨快照重复或不完整");
            }
            for(int i=0;i<pools.size();i++) {
                SeckillActivitySkuDO pool=pools.get(i);SeckillStockReceiptDO receipt=receipts.get(i);SeckillClearingMapper.LedgerBalance balance=balances.get(i);
                ProductSeckillAllocationReqDTO request=bySku.get(pool.getSkuId());
                if(request==null || !Objects.equals(pool.getSkuId(),receipt.getSkuId()) || !Objects.equals(pool.getSkuId(),balance.getSkuId())
                        || !Objects.equals(request.getActivityNo(),activity.getActivityNo()) || !Objects.equals(request.getGeneration(),generation)
                        || !Objects.equals(request.getSpuId(),activity.getProductSpuId()) || !Objects.equals(request.getSpuVersion(),activity.getProductSpuVersion())
                        || !Objects.equals(request.getSkuVersion(),pool.getSkuVersion()) || !Objects.equals(request.getOperationNo(),pool.getAllocationOperationNo())
                        || !Objects.equals(request.getQuantity(),pool.getAllocatedQuantity()) || !Objects.equals(request.getQuantity(),pool.getPlannedQuantity())
                        || !Integer.valueOf(20).equals(pool.getAllocationStatus()) || !Integer.valueOf(0).equals(pool.getAvailableQuantity())
                        || !Integer.valueOf(0).equals(pool.getHeldQuantity()) || !Integer.valueOf(0).equals(pool.getReturnPendingQuantity())
                        || pool.getSoldQuantity()+pool.getReturnedQuantity()!=pool.getAllocatedQuantity()
                        || !Objects.equals(receipt.getAllocatedQuantity(),pool.getAllocatedQuantity()) || !Objects.equals(receipt.getReturnedQuantity(),pool.getReturnedQuantity())
                        || receipt.getProductVersion()==null || receipt.getProductVersion()<1
                        || !Integer.valueOf(pool.getReturnedQuantity()==0?10:pool.getSoldQuantity()==0?30:50).equals(receipt.getProductStatus())
                        || !Objects.equals(balance.getAllocatedQuantity(),pool.getAllocatedQuantity().longValue()) || !Long.valueOf(0).equals(balance.getAvailableQuantity())
                        || !Long.valueOf(0).equals(balance.getHeldQuantity()) || !Objects.equals(balance.getSoldQuantity(),pool.getSoldQuantity().longValue())
                        || !Objects.equals(balance.getReturnedQuantity(),pool.getReturnedQuantity().longValue()))throw new IllegalStateException("原清算池与实际回执/流水不一致");
                SeckillClearingPoolDO row=new SeckillClearingPoolDO();row.setTenantId(tenant);row.setActivityId(activityId);row.setGeneration(generation);
                row.setRedisEpoch(epoch.getEpoch());row.setSkuId(pool.getSkuId());row.setAllocationOperationNo(pool.getAllocationOperationNo());
                row.setAllocationSnapshot(JsonUtils.toJsonString(request));row.setAllocatedQuantity(pool.getAllocatedQuantity());row.setSoldQuantity(pool.getSoldQuantity());
                row.setReturnedQuantity(pool.getReturnedQuantity());row.setProductVersion(receipt.getProductVersion());row.setProductStatus(receipt.getProductStatus());
                row.setDefinitionDigest(epoch.getDefinitionDigest());row.setPoolVersion(pool.getVersion());row.setActivityVersion(activity.getVersion()+1);
                if(clearing.insert(row)!=1)throw new IllegalStateException("原代次清算基线未完整保存");
            }
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",original.getRequestId());details.put("generation",generation);
            details.put("definitionDigest",epoch.getDefinitionDigest());details.put("poolCount",pools.size());
            if(clearing.begin(tenant,activityId,generation,original.getRequestId(),activity.getVersion())!=1
                    || activities.audit(tenant,activityId,0L,"CLEARING",activity.getVersion(),activity.getVersion()+1,JsonUtils.toJsonString(details))!=1)
                throw new IllegalStateException("原清算状态与系统审计未完整保存");
            return sealed(tenant,activityId,generation,epoch);
        });
        if(saved==null)throw new IllegalStateException("原清算提交结果待确认，请按原代次重查");return saved;
    }
    /** 活动池清算完成，不等于订单/售后全部结束；原基线继续承接将来退款。 */
    public void finishClearing(Long activityId,Integer generation) {
        Long tenant=context();if(!id(activityId) || generation==null || generation<1)throw new IllegalArgumentException("需要原活动及代次");
        if(Long.valueOf(1).equals(clearing.completed(tenant,activityId,generation)))return;
        Boolean done=write.execute(tx->{
            if(!Objects.equals(contextTenant(),tenant))throw new IllegalStateException("清算完成租户变化");
            SeckillActivityDO activity=activities.lock(tenant,activityId);
            if(Long.valueOf(1).equals(clearing.completed(tenant,activityId,generation)))return true;
            if(activity==null || !Objects.equals(activity.getGeneration(),generation) || !Integer.valueOf(40).equals(activity.getStatus()))
                throw new IllegalStateException("原代次尚未进入清算，不能结束新活动");
            SeckillRedisEpochDO epoch=epochs.initial(tenant,activityId,generation);
            if(!closed(epoch) || !Objects.equals(epoch.getPublishRequestId(),activity.getPublishOperationNo()))throw new IllegalStateException("原关闭事实变化");
            List<SeckillClearingPoolDO> baseline=sealed(tenant,activityId,generation,epoch);
            if(!Long.valueOf(0).equals(clearing.pendingObligations(tenant,activityId,generation)) || clearing.paidQuotaMatches(tenant,activityId,generation)!=1)
                throw new IllegalStateException("原消息/退款/归还或个人额度仍待核对");
            List<SeckillHistoricalRefundMapper.Progress> progress=history.progressAll(tenant,activityId,generation);
            List<SeckillStockReceiptDO> latest=decisions.latestAll(tenant,activityId,generation);
            if(progress==null || progress.size()>baseline.size() || latest==null || latest.size()!=baseline.size())throw new IllegalStateException("原清算实际商品回执不完整");
            Map<Long,SeckillHistoricalRefundMapper.Progress> bySku=new HashMap<>();
            for(SeckillHistoricalRefundMapper.Progress p:progress) {
                if(p==null || p.getSkuId()==null || bySku.put(p.getSkuId(),p)!=null || p.getReturnedCount()==null || p.getConfirmedPlans()==null
                        || p.getReturnedCount()<0 || p.getConfirmedPlans()<0)throw new IllegalStateException("原历史归还累计数据不完整");
            }
            for(int i=0;i<baseline.size();i++) {
                SeckillClearingPoolDO c=baseline.get(i);SeckillStockReceiptDO actual=latest.get(i);SeckillHistoricalRefundMapper.Progress p=bySku.remove(c.getSkuId());
                long restored=p==null?0:p.getReturnedCount(),confirmed=p==null?0:p.getConfirmedPlans();
                if(!Objects.equals(c.getSkuId(),actual.getSkuId()) || !Objects.equals(c.getActivityVersion(),activity.getVersion())
                        || restored>c.getSoldQuantity() || actual.getReturnedQuantity().longValue()!=c.getReturnedQuantity()+restored
                        || actual.getProductVersion()!=c.getProductVersion()+confirmed || !Objects.equals(actual.getAllocatedQuantity(),c.getAllocatedQuantity())
                        || !Integer.valueOf(actual.getReturnedQuantity()==0?10:actual.getReturnedQuantity().equals(actual.getAllocatedQuantity())?30:50).equals(actual.getProductStatus()))
                    throw new IllegalStateException("原清算基线与实际历史归还不一致");
            }
            if(!bySku.isEmpty())throw new IllegalStateException("原历史归还含不属于清算基线的规格");
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",activity.getPublishOperationNo());details.put("generation",generation);
            details.put("definitionDigest",epoch.getDefinitionDigest());details.put("poolCount",baseline.size());
            if(clearing.complete(tenant,activityId,generation,activity.getPublishOperationNo(),activity.getVersion())!=1
                    || activities.audit(tenant,activityId,0L,"CLEARING_DONE",activity.getVersion(),activity.getVersion()+1,JsonUtils.toJsonString(details))!=1)
                throw new IllegalStateException("原活动结束与清算审计未完整提交");return true;
        });
        if(!Boolean.TRUE.equals(done))throw new IllegalStateException("原活动结束提交结果待查询");
    }
    /** 每项只推进一个阶段；失败也推进扫描位置，下轮绕回后继续按原活动/代次核对。 */
    public BatchResult clearBatch(int limit,Long after,String trace) {
        Long tenant=context();
        if(limit<1 || limit>5 || after!=null && !id(after) || trace==null
                || !trace.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("清算批次或扫描位置不正确");
        List<SeckillActivityDO> rows=clearing.candidates(tenant,after,limit);
        if(rows==null || rows.size()>limit)throw new IllegalStateException("原清算候选批次不完整");
        BatchResult out=new BatchResult();if(!rows.isEmpty())out.nextActivity=after;
        long previous=after==null?0:after,started=System.nanoTime();
        for(SeckillActivityDO row:rows) {
            if(row==null || !Objects.equals(row.getTenantId(),tenant) || !id(row.getId()) || row.getId()<=previous
                    || row.getGeneration()==null || row.getGeneration()<1 || !Arrays.asList(30,40).contains(row.getStatus()))
                throw new IllegalStateException("原清算候选归属或顺序不一致");
            if(System.nanoTime()-started>=15000000000L)break;out.attempted++;
            try {
                if(!Long.valueOf(0).equals(clearing.pendingObligations(tenant,row.getId(),row.getGeneration()))
                        || clearing.paidQuotaMatches(tenant,row.getId(),row.getGeneration())!=1) {
                    out.waiting++; // 已知义务未就绪；不写成功、不清除原失败预算。
                } else if(Integer.valueOf(30).equals(row.getStatus())) {
                    beginClearing(row.getId(),row.getGeneration());out.entered++;
                } else {
                    finishClearing(row.getId(),row.getGeneration());out.completed++;
                }
            } catch(RuntimeException pending) {
                out.pending++;log.warn("Seckill clearing pending tenant={},activity={},generation={},trace={}",tenant,row.getId(),row.getGeneration(),trace);
            }
            previous=row.getId();out.nextActivity=row.getId();
        }
        return out;
    }
    @Data public static class BatchResult {private int attempted;private int entered;private int completed;private int waiting;private int pending;private Long nextActivity;}

    private List<SeckillClearingPoolDO> sealed(Long tenant,Long activity,Integer generation,SeckillRedisEpochDO epoch) {
        List<SeckillClearingPoolDO> rows=clearing.sealed(tenant,activity,generation);
        SeckillPublishedManifest definition=JsonUtils.parseObject(epoch.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        if(rows==null || definition==null || definition.getSkus()==null || rows.isEmpty() || rows.size()>200 || rows.size()!=definition.getSkus().size())
            throw new IllegalStateException("原清算基线或实际系统审计缺失");return rows;
    }
    private boolean closed(SeckillRedisEpochDO e) {
        return e!=null && (Integer.valueOf(30).equals(e.getStatus()) && "SYNCED".equals(e.getStopCacheState())
                || Integer.valueOf(40).equals(e.getStatus()) && "NOT_REQUIRED".equals(e.getStopCacheState()) && e.getInitToken()==null && e.getInitDeadline()==null);
    }
    private Long context() {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("清算准备仅允许事务外可信后台");return contextTenant();
    }
    private Long contextTenant(){Long tenant=TenantContextHolder.getTenantId();if(!id(tenant) || TenantContextHolder.isIgnore())throw new IllegalStateException("需要可信原租户");return tenant;}
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
}
