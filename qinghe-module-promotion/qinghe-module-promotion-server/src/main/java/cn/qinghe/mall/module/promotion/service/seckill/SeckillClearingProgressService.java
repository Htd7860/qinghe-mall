package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillClearingProgressRespVO;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillClearingProgressRespVO.Sku;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 沿用发布历史只读RR/当前商家query权限，按明确原代次读事实，不推进任何业务。 */
@Service
public class SeckillClearingProgressService {
    private final SeckillActivityMapper activities;
    private final SeckillClearingProgressMapper progress;
    private final SeckillClearingMapper clearing;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillPublishDecisionMapper decisions;
    private final SeckillHistoricalRefundMapper history;
    private final SecurityFrameworkService security;
    private final TransactionTemplate read;

    public SeckillClearingProgressService(SeckillActivityMapper activities,SeckillClearingProgressMapper progress,
            SeckillClearingMapper clearing,SeckillRedisEpochMapper epochs,SeckillPublishDecisionMapper decisions,
            SeckillHistoricalRefundMapper history,SecurityFrameworkService security,PlatformTransactionManager manager) {
        this.activities=activities;this.progress=progress;this.clearing=clearing;this.epochs=epochs;
        this.decisions=decisions;this.history=history;this.security=security;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }

    public SeckillClearingProgressRespVO get(Long activityId,Integer generation) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("清算查询不能嵌套外部事务");
        if(!id(activityId) || generation==null || generation<1)throw exception0(400,"需要活动和原发布代次");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null)throw exception0(401,"请先登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || !id(actor.getId()) || !id(actor.getTenantId())
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))
            throw exception0(403,"清算进度仅限当前商家");
        if(!security.hasPermission("promotion:seckill-activity:query"))throw exception0(403,"没有活动查询权限");
        return read.execute(tx->{
            if(TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                    || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
                throw exception0(401,"清算查询身份变化或已到期");
            return snapshot(actor.getTenantId(),activityId,generation);
        });
    }

    private SeckillClearingProgressRespVO snapshot(Long tenant,Long activityId,Integer generation) {
        SeckillActivityDO activity=activities.get(tenant,activityId);
        if(activity==null)throw exception0(404,"活动不存在");
        List<SeckillPublishRecordDO> records=progress.publication(tenant,activityId,generation);
        if(records==null || records.size()>1)throw incomplete();
        if(records.isEmpty())throw exception0(404,"原发布代次不存在");
        SeckillPublishRecordDO original=records.get(0);
        if(!Long.valueOf(1).equals(decisions.completed(tenant,activityId,"PUBLISH",original.getRequestId()))
                || !Long.valueOf(0).equals(decisions.completed(tenant,activityId,"WITHDRAW",original.getRequestId())))
            throw exception0(409,"原代次尚未发布或已开卖前撤回，请查询发布结果");
        ProductSeckillAllocationReqDTO[] requests=JsonUtils.parseObject(original.getRequestSnapshot(),ProductSeckillAllocationReqDTO[].class);
        if(requests==null || requests.length<1 || requests.length>200)throw incomplete();
        Map<Long,Integer> quantities=new TreeMap<>();
        for(ProductSeckillAllocationReqDTO r:requests) {
            if(r==null || !id(r.getSkuId()) || !Objects.equals(r.getGeneration(),generation) || r.getQuantity()==null || r.getQuantity()<1
                    || quantities.put(r.getSkuId(),r.getQuantity())!=null)throw incomplete();
        }
        SeckillRedisEpochDO epoch=epochs.initial(tenant,activityId,generation);
        if(epoch!=null && !Objects.equals(epoch.getPublishRequestId(),original.getRequestId()))throw incomplete();
        List<SeckillClearingPoolDO> baseline=clearing.sealed(tenant,activityId,generation);
        if(baseline==null || !baseline.isEmpty() && baseline.size()!=quantities.size())throw incomplete();
        boolean sealed=!baseline.isEmpty();
        boolean completed=fact(clearing.completed(tenant,activityId,generation));
        boolean reopened=fact(history.reopened(tenant,activityId,generation));
        boolean closed=epoch!=null && (Integer.valueOf(30).equals(epoch.getStatus()) && "SYNCED".equals(epoch.getStopCacheState())
                || Integer.valueOf(40).equals(epoch.getStatus()) && "NOT_REQUIRED".equals(epoch.getStopCacheState()) && epoch.getInitToken()==null && epoch.getInitDeadline()==null);
        boolean current=Objects.equals(activity.getGeneration(),generation) && Objects.equals(activity.getPublishOperationNo(),original.getRequestId());
        if(sealed && !closed || completed && !sealed || reopened && !completed || !sealed && !current)throw incomplete();
        SeckillClearingProgressRespVO out=new SeckillClearingProgressRespVO();
        out.setId(activityId);out.setGeneration(generation);out.setCurrentGeneration(activity.getGeneration());out.setCurrentStatus(activity.getStatus());
        out.setBasis(sealed?"SEALED_BASELINE":"CURRENT_POOL");out.setBaselineSaved(sealed);out.setClearingCompleted(completed);out.setDraftReopened(reopened);out.setRuntimeClosed(closed);
        out.setPending(progress.pending(tenant,activityId,generation));
        if(out.getPending()==null || Arrays.asList(out.getPending().getAdmission(),out.getPending().getRequests(),out.getPending().getOutbox(),
                out.getPending().getResultCache(),out.getPending().getRefunds(),out.getPending().getPoolReturns(),out.getPending().getMemberHeld()).contains(null))throw incomplete();
        int paid=clearing.paidQuotaMatches(tenant,activityId,generation);if(paid!=0 && paid!=1)throw incomplete();out.setPaidQuotaConsistent(paid==1);
        Map<Long,SeckillStockReceiptDO> receipts=new HashMap<>();
        List<SeckillStockReceiptDO> actual=decisions.latestAll(tenant,activityId,generation);
        if(actual==null || actual.size()!=quantities.size())throw incomplete();
        for(SeckillStockReceiptDO r:actual)if(r==null || !quantities.containsKey(r.getSkuId()) || receipts.put(r.getSkuId(),r)!=null)throw incomplete();
        List<Sku> rows=new ArrayList<>();
        if(sealed) {
            Map<Long,SeckillHistoricalRefundMapper.Progress> returned=new HashMap<>();
            List<SeckillHistoricalRefundMapper.Progress> changes=history.progressAll(tenant,activityId,generation);
            if(changes==null || changes.size()>quantities.size())throw incomplete();
            for(SeckillHistoricalRefundMapper.Progress p:changes)if(p==null || p.getReturnedCount()==null || p.getConfirmedPlans()==null
                    || !quantities.containsKey(p.getSkuId()) || returned.put(p.getSkuId(),p)!=null)throw incomplete();
            for(SeckillClearingPoolDO c:baseline) {
                if(c==null || !Objects.equals(quantities.get(c.getSkuId()),c.getAllocatedQuantity()) || epoch==null
                        || !Objects.equals(c.getDefinitionDigest(),epoch.getDefinitionDigest()))throw incomplete();
                SeckillHistoricalRefundMapper.Progress p=returned.get(c.getSkuId());
                long restored=p==null?0:p.getReturnedCount(),plans=p==null?0:p.getConfirmedPlans();
                if(restored<0 || plans<0 || restored>c.getSoldQuantity())throw incomplete();
                Sku row=new Sku();row.setSkuId(c.getSkuId());row.setAllocatedQuantity(c.getAllocatedQuantity());
                row.setAvailableQuantity(0);row.setHeldQuantity(0);row.setReturnPendingQuantity(0);
                row.setSoldQuantity((int)(c.getSoldQuantity()-restored));row.setReturnedQuantity((int)(c.getReturnedQuantity()+restored));
                row.setHistoricalReturnedQuantity(restored);
                row.setReceiptConsistent(matches(receipts.get(c.getSkuId()),row,c.getProductVersion()+plans));rows.add(row);
            }
        } else {
            List<SeckillActivitySkuDO> pools=activities.skus(tenant,activityId);
            if(pools==null || pools.size()!=quantities.size())throw incomplete();
            for(SeckillActivitySkuDO p:pools) {
                if(p==null || !Objects.equals(quantities.get(p.getSkuId()),p.getAllocatedQuantity()))throw incomplete();
                Sku row=new Sku();row.setSkuId(p.getSkuId());row.setAllocatedQuantity(p.getAllocatedQuantity());row.setAvailableQuantity(p.getAvailableQuantity());
                row.setHeldQuantity(p.getHeldQuantity());row.setSoldQuantity(p.getSoldQuantity());row.setReturnedQuantity(p.getReturnedQuantity());
                row.setReturnPendingQuantity(p.getReturnPendingQuantity());row.setHistoricalReturnedQuantity(0L);
                row.setReceiptConsistent(matches(receipts.get(p.getSkuId()),row,null));rows.add(row);
            }
        }
        Set<Long> seen=new HashSet<>();
        for(Sku r:rows) {
            if(!seen.add(r.getSkuId()) || r.getAllocatedQuantity()==null || r.getAvailableQuantity()==null || r.getHeldQuantity()==null
                    || r.getSoldQuantity()==null || r.getReturnedQuantity()==null || r.getReturnPendingQuantity()==null
                    || r.getAvailableQuantity()<0 || r.getHeldQuantity()<0 || r.getSoldQuantity()<0 || r.getReturnedQuantity()<0 || r.getReturnPendingQuantity()<0
                    || r.getReturnPendingQuantity()>r.getAvailableQuantity()
                    || (long)r.getAvailableQuantity()+r.getHeldQuantity()+r.getSoldQuantity()+r.getReturnedQuantity()!=r.getAllocatedQuantity())throw incomplete();
        }
        rows.sort(Comparator.comparing(Sku::getSkuId));out.setSkus(rows);out.setServerTime(System.currentTimeMillis());return out;
    }
    private boolean matches(SeckillStockReceiptDO actual,Sku row,Long expectedVersion) {
        return actual!=null && actual.getProductVersion()!=null && actual.getProductVersion()>0
                && (expectedVersion==null || Objects.equals(actual.getProductVersion(),expectedVersion))
                && Objects.equals(actual.getAllocatedQuantity(),row.getAllocatedQuantity()) && Objects.equals(actual.getReturnedQuantity(),row.getReturnedQuantity())
                && Integer.valueOf(row.getReturnedQuantity()==0?10:row.getReturnedQuantity().equals(row.getAllocatedQuantity())?30:50).equals(actual.getProductStatus());
    }
    private boolean fact(Long count){if(count==null || count<0 || count>1)throw incomplete();return count==1;}
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"原代次清算记录不完整，请稍后核对");}
}
