package cn.qinghe.mall.module.promotion.service.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillEventDTO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.trade.SeckillOrderResultClient;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原库存条件增减+事务流水职责；结果先来源证明，再在原活动父锁内推进，未接MQ消费者。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillOrderResultService {
    private final SeckillOrderResultClient trade;
    private final SeckillOrderResultMapper results;
    private final SeckillAdmissionMapper admissions;
    private final SeckillActivityMapper activities;
    private final SeckillAdmissionIntentMapper intents;
    private final SeckillClosedCacheSyncService cache;
    private final TransactionTemplate write;
    public SeckillOrderResultService(SeckillOrderResultClient trade,SeckillOrderResultMapper results,SeckillAdmissionMapper admissions,
            SeckillActivityMapper activities,SeckillAdmissionIntentMapper intents,SeckillClosedCacheSyncService cache,PlatformTransactionManager manager) {
        this.trade=trade;this.results=results;this.admissions=admissions;this.activities=activities;this.intents=intents;this.cache=cache;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public SeckillOrderResultDO process(String body) {
        SeckillEventDTO event=SeckillEventEnvelope.decode(body);Long tenant=context(event);String hash=DigestUtil.sha256Hex(body);
        SeckillOrderResultDO done=results.receipt(tenant,event.getEventId());
        if (done!=null) return finish(sameReceipt(done,event,hash));
        SeckillOrderResultSnapshotDTO proof=trade.verify(body); // RPC在所有本地事务/锁之外。
        SeckillRequestDO route=admissions.forDispatch(tenant,event.getRequestNo());
        if (route==null) throw incomplete();SeckillRequestReceiptService.project(route);
        SeckillOrderResultDO out=write.execute(tx -> apply(event,hash,proof,route));
        if (out==null) throw new IllegalStateException("秒杀结果处理没有返回最终回执");return finish(out);
    }
    /** 数据库结果已提交；缓存故障保留持久PENDING，不能谎称上面的业务事务回滚。 */
    private SeckillOrderResultDO finish(SeckillOrderResultDO receipt) {
        if (Integer.valueOf(20).equals(receipt.getResultState()) && "PENDING".equals(receipt.getCacheSyncState())) {
            try {
                cache.synchronize(receipt.getEventId());
                SeckillOrderResultDO current=results.receipt(receipt.getTenantId(),receipt.getEventId());
                if (current==null) throw incomplete();return current;
            } catch (RuntimeException pending) {
                log.warn("Seckill result committed, closed cache acknowledgement pending tenant={},request={},event={}",receipt.getTenantId(),receipt.getRequestNo(),receipt.getEventId());
            }
        }
        return receipt;
    }
    private SeckillOrderResultDO apply(SeckillEventDTO event,String hash,SeckillOrderResultSnapshotDTO proof,SeckillRequestDO route) {
        Long tenant=event.getTenantId();
        if (!Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()) throw exception0(403,"结果租户上下文已变化");
        SeckillActivityDO activity=activities.lock(tenant,route.getActivityId());
        if (activity==null || !Objects.equals(activity.getGeneration(),route.getGeneration())) throw incomplete();
        SeckillActivitySkuDO pool=admissions.lockPool(tenant,route.getActivityId(),route.getSkuId());
        SeckillMemberQuotaDO quota=admissions.lockQuota(tenant,route.getActivityId(),route.getGeneration(),route.getMemberId());
        SeckillRequestDO r=results.lockRequest(tenant,event.getRequestNo());
        SeckillOrderResultDO prior=results.receipt(tenant,event.getEventId());
        if (prior!=null) return sameReceipt(prior,event,hash);
        if (r==null || pool==null || quota==null || !Objects.equals(r.getActivityId(),route.getActivityId())
                || !Objects.equals(r.getGeneration(),route.getGeneration()) || !Objects.equals(r.getSkuId(),route.getSkuId())
                || !Objects.equals(r.getMemberId(),route.getMemberId())) throw incomplete();
        SeckillRequestReceiptService.project(r);
        SeckillAdmissionIntentDO intent=intents.get(tenant,r.getMemberId(),r.getClientRequestId());
        if (intent==null || !Integer.valueOf(10).equals(intent.getStatus()) || !Objects.equals(intent.getRequestNo(),r.getRequestNo())
                || !Objects.equals(intent.getRequestDigest(),r.getRequestDigest())) throw incomplete();
        SeckillQuoteBindingRespDTO b=proof.getBinding();sameOriginal(r,b);
        boolean created=Integer.valueOf(10).equals(b.getState());
        if (created) {
            if (!proof.getOrderCreatedAt().toInstant().equals(r.getAcceptedTime().toInstant(ZoneOffset.UTC))
                    || !proof.getPayDeadline().toInstant().equals(r.getPayDeadline().toInstant(ZoneOffset.UTC))
                    || Integer.valueOf(40).equals(r.getStatus())
                    || Integer.valueOf(50).equals(r.getStatus()) && !("CANCELLED".equals(r.getResourceState()) && Objects.equals(r.getOrderId(),b.getOrderId()))
                    || r.getOrderId()!=null && !Objects.equals(r.getOrderId(),b.getOrderId())) throw incomplete();
            if (r.getOrderId()==null) {
                if (results.bindOrder(r,b.getOrderId(),b.getFenceVersion())!=1
                        || results.journal(r,"ORDER_CREATED",0,event.getEventId())!=1) throw new IllegalStateException("原秒杀订单绑定及流水失败");
            }
            // 已进入关闭/未知阶段的申请只补绑定，绝不因迟到CREATED重新开放支付或释放库存。
        } else {
            if (r.getOrderId()!=null || !Arrays.asList(10,30,40,50,60).contains(r.getStatus())) throw incomplete();
            if (!Integer.valueOf(50).equals(r.getStatus())) {
                if (pool.getHeldQuantity()<r.getQuantity() || quota.getHeldQuantity()<r.getQuantity()) throw incomplete();
                if (results.releasePool(r,pool.getVersion())!=1 || results.releaseMember(r,quota.getVersion())!=1
                        || results.closeBeforeOrder(r,b.getFenceVersion())!=1
                        || results.journal(r,"CREATE_CLOSED",r.getQuantity(),event.getEventId())!=1)
                    throw new IllegalStateException("原建单前关闭与占用退回未完整写入");
            }
            // paid_quantity不回减；Redis仍可能保留R/C占用，最终回执留下PENDING供后续原号同步。
        }
        SeckillRequestDO current=results.lockRequest(tenant,event.getRequestNo());
        SeckillOrderResultDO receipt=new SeckillOrderResultDO();receipt.setTenantId(tenant);receipt.setEventId(event.getEventId());
        receipt.setRequestNo(event.getRequestNo());receipt.setAggregateVersion(event.getAggregateVersion());receipt.setEventType(event.getEventType());
        receipt.setPayloadHash(hash);receipt.setResultState(b.getState());receipt.setOrderId(b.getOrderId());
        receipt.setRequestVersion(current.getVersion());receipt.setCacheSyncState(created?"NOT_REQUIRED":"PENDING");
        if (results.complete(receipt)!=1) throw new IllegalStateException("原秒杀结果最终回执保存失败");
        return results.receipt(tenant,event.getEventId());
    }
    private void sameOriginal(SeckillRequestDO r,SeckillQuoteBindingRespDTO b) {
        if (!Objects.equals(r.getTenantId(),b.getTenantId()) || !Objects.equals(r.getMemberId(),b.getMemberId())
                || !Objects.equals(r.getRequestNo(),b.getRequestNo()) || !Objects.equals(r.getClientRequestId(),b.getClientRequestId())
                || !Objects.equals(r.getRequestDigest(),b.getRequestDigest()) || !Objects.equals(r.getQuoteId(),b.getQuoteId())
                || !Objects.equals(r.getQuoteDigest(),b.getQuoteDigest())) throw incomplete();
        if (b.getActivityId()==null) { if (!Integer.valueOf(20).equals(b.getState())) throw incomplete();return; }
        if (!Objects.equals(r.getActivityId(),b.getActivityId()) || !Objects.equals(r.getGeneration(),b.getGeneration())
                || !Objects.equals(r.getActivityVersion(),b.getActivityVersion()) || !Objects.equals(r.getRedisEpoch(),b.getRedisEpoch())
                || !Objects.equals(r.getSkuId(),b.getSkuId()) || !Objects.equals(r.getQuantity(),b.getQuantity())
                || !Objects.equals(r.getPriceCent(),b.getPriceCent()) || !Objects.equals(r.getGoodsAmount(),b.getGoodsAmount())
                || !Objects.equals(r.getShippingAmount(),b.getShippingAmount()) || !Objects.equals(r.getPayAmount(),b.getPayAmount())
                || b.getQuoteExpiresAt()==null || !r.getQuoteExpiresTime().toInstant(ZoneOffset.UTC).equals(b.getQuoteExpiresAt().toInstant())) throw incomplete();
    }
    private SeckillOrderResultDO sameReceipt(SeckillOrderResultDO r,SeckillEventDTO e,String hash) {
        if (!Objects.equals(r.getTenantId(),e.getTenantId()) || !Objects.equals(r.getEventId(),e.getEventId())
                || !Objects.equals(r.getRequestNo(),e.getRequestNo()) || !Objects.equals(r.getAggregateVersion(),e.getAggregateVersion())
                || !Objects.equals(r.getEventType(),e.getEventType()) || !Objects.equals(r.getPayloadHash(),hash)) throw exception0(409,"原结果事件不可更换参数");
        return r;
    }
    private Long context(SeckillEventDTO e) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("结果处理只能在事务外可信后台执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || !Objects.equals(tenant,e.getTenantId())) throw exception0(403,"结果租户上下文不正确");
        if (!Arrays.asList("SECKILL_ORDER_CREATED","SECKILL_REQUEST_CLOSED").contains(e.getEventType())) throw exception0(400,"需要建单结果事件");return tenant;
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原秒杀结果、申请或占用账本不一致，请按原申请核查"); }
}
