package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillInitialRuntimeRedisDAO;
import cn.qinghe.mall.module.promotion.framework.product.SeckillStockClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import javax.validation.Validator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import static cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO.*;

/** 复用原冻结发布/条件池更新与库存RPC；预留先提交，商品确认后本库池/回执/流水同事务记账。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillPoolReturnService {
    private final SeckillActivityMapper activities;
    private final SeckillAdmissionMapper admissions;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillPublishMapper publishes;
    private final SeckillStockResultMapper receipts;
    private final SeckillPoolReturnMapper returns;
    private final SeckillInitialRuntimeRedisDAO runtime;
    private final Validator validator;
    private final SeckillStockClient stock;
    private final TransactionTemplate write;
    public SeckillPoolReturnService(SeckillActivityMapper activities,SeckillAdmissionMapper admissions,SeckillRedisEpochMapper epochs,
            SeckillPublishMapper publishes,SeckillStockResultMapper receipts,SeckillPoolReturnMapper returns,
            SeckillInitialRuntimeRedisDAO runtime,Validator validator,SeckillStockClient stock,PlatformTransactionManager manager) {
        this.activities=activities;this.admissions=admissions;this.epochs=epochs;this.publishes=publishes;this.receipts=receipts;
        this.returns=returns;this.runtime=runtime;this.validator=validator;this.stock=stock;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** 后续固定后台按原returnNo推进；没有消费者/商家HTTP或自由数量参数。 */
    public SeckillPoolReturnDO prepare(Long activityId,Long skuId,String returnNo) {
        Long tenant=context();
        if(!id(activityId) || !id(skuId) || !uuid(returnNo))throw new IllegalArgumentException("需要原活动、规格及归还号");
        SeckillPoolReturnDO previous=returns.get(tenant,returnNo);
        if(previous!=null)return sameOriginal(previous,activityId,skuId);
        SeckillActivityDO before=activities.get(tenant,activityId);
        if(before==null || !Integer.valueOf(30).equals(before.getStatus()))throw new IllegalStateException("原活动尚未持久停止新受理");
        SeckillRedisEpochDO closed=epochs.initial(tenant,activityId,before.getGeneration());
        if(closed==null || !Arrays.asList(30,40).contains(closed.getStatus()))throw new IllegalStateException("原epoch尚未停止");
        runtime.close(closed); // Redis关闭不可逆；网络在数据库事务/池锁之外，失败不预留。
        SeckillPoolReturnDO prepared=write.execute(tx->{
            if(!Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw new IllegalStateException("归还准备租户变化");
            SeckillActivityDO activity=activities.lock(tenant,activityId);
            if(activity==null)throw new IllegalStateException("原活动缺失");
            SeckillPoolReturnDO duplicate=returns.get(tenant,returnNo);
            if(duplicate!=null)return sameOriginal(duplicate,activityId,skuId);
            SeckillRedisEpochDO epoch=epochs.initial(tenant,activityId,activity.getGeneration());
            if(!Integer.valueOf(30).equals(activity.getStatus()) || !Objects.equals(activity.getGeneration(),closed.getGeneration())
                    || !Objects.equals(activity.getRedisEpoch(),closed.getEpoch()) || epoch==null || !Arrays.asList(30,40).contains(epoch.getStatus())
                    || !Objects.equals(epoch.getInitToken(),closed.getInitToken()) || !Objects.equals(epoch.getDefinitionDigest(),closed.getDefinitionDigest())
                    || !Objects.equals(activity.getPublishOperationNo(),epoch.getPublishRequestId()))
                throw new IllegalStateException("原停止代次变化，不能预留当前新池");
            SeckillActivitySkuDO pool=admissions.lockPool(tenant,activityId,skuId);
            if(pool==null || !Integer.valueOf(20).equals(pool.getAllocationStatus()))throw new IllegalStateException("原已划拨规格池缺失");
            if(returns.pending(tenant,activityId,activity.getGeneration(),skuId)!=null)
                throw new IllegalStateException("已有原归还请求待确认，请恢复原归还号");
            if(pool.getReturnPendingQuantity()!=0)throw new IllegalStateException("原归还预留缺少匹配凭证，需核查");
            int quantity=pool.getAvailableQuantity()-pool.getReturnPendingQuantity();
            if(quantity<=0)throw new IllegalStateException("本代次目前没有可归还的空闲库存");
            SeckillPublishRecordDO publish=publishes.get(tenant,activity.getPublishOperationNo());
            ProductSeckillAllocationReqDTO allocation=frozen(activity,pool,publish);
            SeckillStockReceiptDO last=receipts.latest(tenant,activityId,activity.getGeneration(),skuId);
            if(last==null || !Arrays.asList(ALLOCATED,RETURNING,RETURNED).contains(last.getProductStatus())
                    || !Objects.equals(last.getAllocatedQuantity(),pool.getAllocatedQuantity())
                    || !Objects.equals(last.getReturnedQuantity(),pool.getReturnedQuantity())
                    || last.getProductVersion()==null || last.getProductVersion()<1 || last.getProductVersion()>=9007199254740991L)
                throw new IllegalStateException("原实际划拨累计回执与池不一致，不能按计划量归还");
            SeckillPoolReturnDO plan=new SeckillPoolReturnDO();plan.setTenantId(tenant);plan.setReturnNo(returnNo);plan.setActivityId(activityId);
            plan.setGeneration(activity.getGeneration());plan.setRedisEpoch(activity.getRedisEpoch());plan.setSkuId(skuId);
            plan.setAllocationOperationNo(allocation.getOperationNo());plan.setAllocationSnapshot(JsonUtils.toJsonString(allocation));
            plan.setQuantity(quantity);plan.setBaseReturned(pool.getReturnedQuantity());plan.setTargetReturned(Math.addExact(pool.getReturnedQuantity(),quantity));
            plan.setBaseProductVersion(last.getProductVersion());
            if(plan.getTargetReturned()>pool.getAllocatedQuantity() || returns.reserve(plan,pool.getVersion())!=1 || returns.insert(plan)!=1)
                throw new IllegalStateException("原归还预留与不可变累计目标未同时保存");
            return returns.get(tenant,returnNo);
        });
        if(prepared==null)throw new IllegalStateException("原归还准备结果待确认，请按原归还号查询");
        return prepared;
    }

    /** 先查原商品凭证，再同号推进；未知响应保留PENDING与预留，不换号或声称库存回滚。 */
    public SeckillPoolReturnDO advance(String returnNo) {
        Long tenant=context();if(!uuid(returnNo))throw new IllegalArgumentException("需要原归还号");
        SeckillPoolReturnDO plan=returns.get(tenant,returnNo);
        if(plan==null || !"POOL".equals(plan.getSourceKind()))throw new IllegalStateException("原池归还准备尚未确认或来源不匹配");
        if("CONFIRMED".equals(plan.getState()))return synchronizeIfPossible(confirmed(tenant,returnNo));
        if(!"PENDING".equals(plan.getState()))throw new IllegalStateException("原归还状态不支持自动推进");
        SeckillActivityDO before=activities.get(tenant,plan.getActivityId());
        if(before==null || !Integer.valueOf(30).equals(before.getStatus()) || !Objects.equals(before.getGeneration(),plan.getGeneration()))
            throw new IllegalStateException("原停止代次变化，不能提交归还");
        SeckillRedisEpochDO epoch=epochs.initial(tenant,plan.getActivityId(),plan.getGeneration());
        if(epoch==null || !Arrays.asList(30,40).contains(epoch.getStatus()) || !Objects.equals(epoch.getEpoch(),plan.getRedisEpoch()))
            throw new IllegalStateException("原停止epoch缺失");
        runtime.close(epoch);
        ProductSeckillAllocationRespDTO response=stock.returnResult(returnNo); // 所有网络均在本库事务/锁外。
        SeckillPoolReturnDO observed=returns.get(tenant,returnNo);
        if(observed!=null && "CONFIRMED".equals(observed.getState()))return synchronizeIfPossible(confirmed(tenant,returnNo));
        if(response==null)throw new IllegalStateException("原实际商品划拨缺失，保留预留待核查");
        if(Objects.equals(response.getReturnedQuantity(),plan.getBaseReturned())) {
            if(!Objects.equals(response.getVersion(),plan.getBaseProductVersion()) || !Arrays.asList(ALLOCATED,RETURNING).contains(response.getStatus()))
                throw new IllegalStateException("原商品归还基线不一致");
            response=stock.returnStock(returnNo);
        }
        ProductSeckillAllocationRespDTO actual=response;
        ProductSeckillAllocationReqDTO original=JsonUtils.parseObject(plan.getAllocationSnapshot(),ProductSeckillAllocationReqDTO.class);
        if(original==null || !Objects.equals(actual.getAllocatedQuantity(),original.getQuantity())
                || !Objects.equals(actual.getReturnedQuantity(),plan.getTargetReturned()) || actual.getVersion()!=plan.getBaseProductVersion()+1
                || !Integer.valueOf(plan.getTargetReturned().equals(original.getQuantity())?RETURNED:RETURNING).equals(actual.getStatus()))
            throw new IllegalStateException("原累计归还目标或商品回执版本不一致，保留待核查");
        SeckillPoolReturnDO done=write.execute(tx->{
            if(!Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw new IllegalStateException("归还确认租户变化");
            SeckillActivityDO activity=activities.lock(tenant,plan.getActivityId());
            if(activity==null)throw new IllegalStateException("原活动缺失");
            SeckillActivitySkuDO pool=admissions.lockPool(tenant,plan.getActivityId(),plan.getSkuId());
            SeckillPoolReturnDO current=returns.lock(tenant,returnNo);
            if(current!=null && "CONFIRMED".equals(current.getState()))return confirmed(tenant,returnNo);
            if(current==null || !"PENDING".equals(current.getState()) || !Objects.equals(current.getVersion(),plan.getVersion())
                    || !Objects.equals(current.getAllocationSnapshot(),plan.getAllocationSnapshot()) || !Objects.equals(current.getTargetReturned(),plan.getTargetReturned())
                    || !Integer.valueOf(30).equals(activity.getStatus()) || !Objects.equals(activity.getGeneration(),plan.getGeneration())
                    || !Objects.equals(activity.getRedisEpoch(),plan.getRedisEpoch()) || pool==null
                    || !Objects.equals(pool.getAllocationOperationNo(),plan.getAllocationOperationNo()) || !Objects.equals(pool.getReturnedQuantity(),plan.getBaseReturned())
                    || !Objects.equals(pool.getReturnPendingQuantity(),plan.getQuantity()) || pool.getAvailableQuantity()<plan.getQuantity())
                throw new IllegalStateException("商品已归还，原池预留或代次仍待核对，不能记为回滚");
            SeckillStockReceiptDO last=receipts.latest(tenant,plan.getActivityId(),plan.getGeneration(),plan.getSkuId());
            if(last==null || !Objects.equals(last.getProductVersion(),plan.getBaseProductVersion()) || !Objects.equals(last.getReturnedQuantity(),plan.getBaseReturned())
                    || !Objects.equals(last.getAllocatedQuantity(),actual.getAllocatedQuantity()))throw new IllegalStateException("原商品回执基线变化");
            SeckillStockReceiptDO receipt=new SeckillStockReceiptDO();receipt.setTenantId(tenant);receipt.setActivityId(plan.getActivityId());
            receipt.setGeneration(plan.getGeneration());receipt.setSkuId(plan.getSkuId());receipt.setProductVersion(actual.getVersion());receipt.setProductStatus(actual.getStatus());
            receipt.setAllocatedQuantity(actual.getAllocatedQuantity());receipt.setReturnedQuantity(actual.getReturnedQuantity());
            if(returns.apply(current,pool.getVersion())!=1 || receipts.insert(receipt)!=1 || returns.confirmed(current,actual.getVersion(),actual.getStatus())!=1
                    || returns.journal(current)!=1)throw new IllegalStateException("原池归还、商品回执、进度及流水未完整提交");
            return confirmed(tenant,returnNo); // 缓存仍PENDING，不能把本库入账当作完整清算。
        });
        if(done==null)throw new IllegalStateException("原归还确认结果待核对，请按原号查询");return synchronizeIfPossible(done);
    }
    /** 商品和本库已经提交；缓存失败只能保留待同步，不能描述成库存回滚。 */
    private SeckillPoolReturnDO synchronizeIfPossible(SeckillPoolReturnDO done) {
        try {synchronize(done.getReturnNo());return confirmed(done.getTenantId(),done.getReturnNo());}
        catch(RuntimeException pending) {
            log.warn("Seckill pool return cache pending tenant={},return={}",done.getTenantId(),done.getReturnNo());
            return done;
        }
    }
    public void synchronize(String returnNo) {
        Long tenant=context();if(!uuid(returnNo))throw new IllegalArgumentException("需要原归还号");
        SeckillPoolReturnDO proof=confirmed(tenant,returnNo);
        if("SYNCED".equals(proof.getCacheState()))return;
        SeckillRedisEpochDO epoch=epochs.initial(tenant,proof.getActivityId(),proof.getGeneration());
        if(epoch==null || !Arrays.asList(30,40).contains(epoch.getStatus()) || !Objects.equals(epoch.getEpoch(),proof.getRedisEpoch()))
            throw new IllegalStateException("原停止epoch缺失，不能更新其他代次缓存");
        runtime.close(epoch);
        runtime.returnPool(epoch,returnNo);
        if(!Objects.equals(context(),tenant))throw new IllegalStateException("原归还缓存同步租户变化");
        Integer changed=write.execute(tx->returns.cacheSynced(proof));
        if(Integer.valueOf(1).equals(changed))return;
        SeckillPoolReturnDO current=confirmed(tenant,returnNo);
        if("SYNCED".equals(current.getCacheState()) && Objects.equals(current.getProductVersion(),proof.getProductVersion())
                && Objects.equals(current.getTargetReturned(),proof.getTargetReturned()))return;
        throw new IllegalStateException("Redis原归还已同步，本库进度仍待确认，请恢复原号");
    }
    /** 固定后台保存nextReturn游标；失败也越过，空页后从头轮转。 */
    public BatchResult recoverBatch(int limit,String after,String trace) {
        Long tenant=context();
        if(limit<1 || limit>20 || after!=null && !uuid(after) || !uuid(trace))throw new IllegalArgumentException("归还恢复批次或游标不正确");
        List<SeckillPoolReturnDO> rows=returns.recoverable(tenant,after,limit);
        if(rows==null || rows.size()>limit)throw new IllegalStateException("原归还恢复批次不完整");
        BatchResult out=new BatchResult();if(!rows.isEmpty())out.nextReturn=after;long started=System.nanoTime();
        for(SeckillPoolReturnDO row:rows) {
            if(row==null || !Objects.equals(row.getTenantId(),tenant) || !uuid(row.getReturnNo()))throw new IllegalStateException("原归还恢复记录归属不一致");
            if(System.nanoTime()-started>=45000000000L)break;out.attempted++;
            try {
                SeckillPoolReturnDO done;
                if("PENDING".equals(row.getState()))done=advance(row.getReturnNo());
                else {synchronize(row.getReturnNo());done=confirmed(tenant,row.getReturnNo());}
                if("SYNCED".equals(done.getCacheState()))out.completed++;else out.pending++;
            } catch(RuntimeException pending) {
                out.pending++;log.warn("Seckill pool return recovery pending tenant={},return={},trace={}",tenant,row.getReturnNo(),trace);
            }
            out.nextReturn=row.getReturnNo();
        }
        return out;
    }
    @Data public static class BatchResult {private int attempted;private int completed;private int pending;private String nextReturn;}
    /** 只发现停止原池，锁外扫描不授权写入；prepare内重新核对原代次/回执/可归还量。 */
    public PreparationResult prepareBatch(int limit,Long after,String trace) {
        Long tenant=context();
        if(limit<1 || limit>5 || after!=null && !id(after) || !uuid(trace))throw new IllegalArgumentException("归还准备批次或游标不正确");
        List<SeckillActivitySkuDO> rows=returns.prepareCandidates(tenant,after,limit);
        if(rows==null || rows.size()>limit)throw new IllegalStateException("原可归还池批次不完整");
        PreparationResult out=new PreparationResult();if(!rows.isEmpty())out.nextPoolId=after;
        long previous=after==null?0:after,started=System.nanoTime();
        for(SeckillActivitySkuDO row:rows) {
            if(row==null || !Objects.equals(row.getTenantId(),tenant) || !id(row.getId()) || row.getId()<=previous
                    || !id(row.getActivityId()) || !id(row.getSkuId()))throw new IllegalStateException("原可归还池归属或顺序不一致");
            if(System.nanoTime()-started>=15000000000L)break;out.attempted++;
            try {
                // 一次新准备只生成一个号；未知提交留给持久原记录恢复，不换号再提交。
                prepare(row.getActivityId(),row.getSkuId(),UUID.randomUUID().toString());out.prepared++;
            } catch(RuntimeException pending) {
                out.pending++;log.warn("Seckill pool return preparation pending tenant={},activity={},sku={},trace={}",tenant,row.getActivityId(),row.getSkuId(),trace);
            }
            previous=row.getId();out.nextPoolId=row.getId();
        }
        return out;
    }
    @Data public static class PreparationResult {private int attempted;private int prepared;private int pending;private Long nextPoolId;}
    private SeckillPoolReturnDO confirmed(Long tenant,String returnNo) {
        SeckillPoolReturnDO proof=returns.confirmedProof(tenant,returnNo);
        if(proof==null)throw new IllegalStateException("原归还确认缺少实际商品回执或额度流水");return proof;
    }

    public SeckillPoolReturnDO get(String returnNo) {
        Long tenant=context();if(!uuid(returnNo))throw new IllegalArgumentException("需要原归还号");
        SeckillPoolReturnDO row=returns.get(tenant,returnNo);
        if(row!=null && !"POOL".equals(row.getSourceKind()))throw new IllegalStateException("该归还号不是原池归还");
        return row!=null && "CONFIRMED".equals(row.getState())?confirmed(tenant,returnNo):row;
    }
    private ProductSeckillAllocationReqDTO frozen(SeckillActivityDO activity,SeckillActivitySkuDO pool,SeckillPublishRecordDO record) {
        if(record==null || !Objects.equals(record.getActivityId(),activity.getId()) || !Objects.equals(record.getTenantId(),activity.getTenantId())
                || !Objects.equals(record.getGeneration(),activity.getGeneration()) || !Objects.equals(record.getRequestId(),activity.getPublishOperationNo()))
            throw new IllegalStateException("原冻结发布凭证缺失");
        ProductSeckillAllocationReqDTO[] all=JsonUtils.parseObject(record.getRequestSnapshot(),ProductSeckillAllocationReqDTO[].class);
        if(all==null || all.length<1 || all.length>200)throw new IllegalStateException("原冻结规格请求不完整");
        Set<Long> skuIds=new HashSet<>();Set<String> operations=new HashSet<>();ProductSeckillAllocationReqDTO found=null;
        for(ProductSeckillAllocationReqDTO original:all) {
            if(original==null || !validator.validate(original).isEmpty() || !skuIds.add(original.getSkuId()) || !operations.add(original.getOperationNo())
                    || !Objects.equals(original.getActivityNo(),activity.getActivityNo()) || !Objects.equals(original.getGeneration(),activity.getGeneration()))
                throw new IllegalStateException("原发布规格重复或归属不一致");
            if(Objects.equals(original.getSkuId(),pool.getSkuId()))found=original;
        }
        if(found==null || !Objects.equals(found.getSpuId(),activity.getProductSpuId()) || !Objects.equals(found.getSpuVersion(),activity.getProductSpuVersion())
                || !Objects.equals(found.getSkuVersion(),pool.getSkuVersion()) || !Objects.equals(found.getOperationNo(),pool.getAllocationOperationNo())
                || !Objects.equals(found.getQuantity(),pool.getPlannedQuantity()) || !Objects.equals(found.getQuantity(),pool.getAllocatedQuantity()))
            throw new IllegalStateException("原划拨数量/版本/业务号与当前原代次池不一致");
        return found;
    }
    private SeckillPoolReturnDO sameOriginal(SeckillPoolReturnDO plan,Long activity,Long sku) {
        if(!"POOL".equals(plan.getSourceKind()) || !Objects.equals(plan.getActivityId(),activity) || !Objects.equals(plan.getSkuId(),sku))throw new IllegalStateException("原归还号不能绑定其他来源、活动或规格");
        return "CONFIRMED".equals(plan.getState())?confirmed(plan.getTenantId(),plan.getReturnNo()):plan;
    }
    private Long context() {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("归还准备仅限事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();if(!id(tenant) || TenantContextHolder.isIgnore())throw new IllegalStateException("需要可信当前租户");return tenant;
    }
    private boolean id(Long value){return value!=null && value>0 && value<=9007199254740991L;}
    private boolean uuid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");}
}
