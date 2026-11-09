package cn.qinghe.mall.module.promotion.dal.redis.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.config.SeckillAdmissionProperties;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillAdmissionIntentService;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillQuoteBindingRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 先从本库验证原持久意图，再以单hash/单HSET预占或变更标记；不在数据库事务内访问Redis。 */
@Repository @EnableConfigurationProperties(SeckillAdmissionProperties.class)
public class SeckillAdmissionRedisDAO {
    private static final DefaultRedisScript<Long> SCRIPT;
    static { SCRIPT=new DefaultRedisScript<>();SCRIPT.setLocation(new ClassPathResource("lua/seckill-admission-reservation.lua"));SCRIPT.setResultType(Long.class); }
    private final StringRedisTemplate redis;
    private final SeckillAdmissionIntentMapper intents;
    private final SeckillAdmissionMapper admissions;
    private final SeckillOrderResultMapper results;
    private final SeckillOrderResourceMapper resources;
    private final SeckillRefundRecordMapper refunds;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillPublishedManifestRedisDAO manifests;
    private final SeckillAdmissionProperties limits;
    private final String prefix;
    public SeckillAdmissionRedisDAO(StringRedisTemplate redis,SeckillAdmissionIntentMapper intents,SeckillAdmissionMapper admissions,
            SeckillOrderResultMapper results,SeckillOrderResourceMapper resources,SeckillRefundRecordMapper refunds,SeckillRedisEpochMapper epochs,SeckillPublishedManifestRedisDAO manifests,SeckillAdmissionProperties limits,@Value("${qinghe.redis.key-prefix}") String prefix) {
        if (prefix==null || prefix.length()>128 || !prefix.matches("[A-Za-z0-9:_-]+") || prefix.endsWith(":")) throw new IllegalArgumentException("Redis Key前缀配置不合法");
        this.redis=redis;this.intents=intents;this.admissions=admissions;this.results=results;this.epochs=epochs;this.manifests=manifests;this.limits=limits;this.prefix=prefix;
        this.resources=resources;
        this.refunds=refunds;
    }
    public void reserve(SeckillAdmissionIntentDO original) { requireSuccess(execute(original,"RESERVE"),true); }
    public void confirm(SeckillAdmissionIntentDO original) { requireSuccess(execute(original,"CONFIRM"),false); }
    public void releaseRejected(SeckillAdmissionIntentDO original,SeckillQuoteBindingRespDTO closed) {
        if (original==null || closed==null || !Integer.valueOf(20).equals(closed.getState()) || closed.getOrderId()!=null
                || closed.getFenceVersion()==null || closed.getFenceVersion()<1 || closed.getClosedAt()==null
                || !Objects.equals(closed.getTenantId(),original.getTenantId()) || !Objects.equals(closed.getMemberId(),original.getMemberId())
                || !Objects.equals(closed.getRequestNo(),original.getRequestNo()) || !Objects.equals(closed.getClientRequestId(),original.getClientRequestId())
                || !Objects.equals(closed.getRequestDigest(),original.getRequestDigest())) throw exception0(503,"需要原trade永久关闭回执，不能释放预占");
        SeckillRequestDO frozen=SeckillAdmissionIntentService.frozen(original);
        if (!Objects.equals(closed.getQuoteId(),frozen.getQuoteId()) || !Objects.equals(closed.getQuoteDigest(),frozen.getQuoteDigest())) throw exception0(503,"原关闭报价不匹配");
        requireSuccess(execute(original,"RELEASE"),false);
    }

    /** 已受理关闭必须读本库最终结果+永久关闭申请+真实退占用流水，不能使用未受理拒绝接口。 */
    public void releaseClosed(String eventId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Redis关闭同步必须在数据库事务外");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()) throw exception0(403,"关闭同步需要可信当前租户");
        if (eventId==null || !eventId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw exception0(400,"需要原关闭结果事件号");
        SeckillRequestDO proof=results.closedCacheProof(tenant,eventId);
        if (proof==null) throw exception0(503,"缺少原永久关闭及占用退回证明，不能释放Redis");
        SeckillAdmissionIntentDO original=intents.get(tenant,proof.getMemberId(),proof.getClientRequestId());
        if (original==null || !Objects.equals(original.getRequestNo(),proof.getRequestNo())
                || !Objects.equals(original.getRequestDigest(),proof.getRequestDigest())) throw exception0(503,"原关闭申请与受理意图不一致");
        requireSuccess(execute(original,"CLOSE_ACCEPTED",proof),false);
    }

    /** 只有本库终局与同事务资源流水匹配才允许缓存转换；缺失Key不重建余额。 */
    public void synchronizeOrder(String requestNo) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("订单缓存转换必须在数据库事务外");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()) throw exception0(403,"需要可信当前租户");
        if (requestNo==null || !requestNo.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw exception0(400,"需要原申请号");
        SeckillRequestDO proof=resources.cacheProof(tenant,requestNo);
        if (proof==null) throw exception0(503,"原订单资源终局及流水尚未确认");
        SeckillAdmissionIntentDO original=intents.get(tenant,proof.getMemberId(),proof.getClientRequestId());
        if (original==null || !Objects.equals(original.getRequestNo(),proof.getRequestNo()) || !Objects.equals(original.getRequestDigest(),proof.getRequestDigest()))
            throw exception0(503,"原资源终局与受理意图不一致");
        requireSuccess(execute(original,"PAID".equals(proof.getResourceState())?"PAY_ACCEPTED":"CANCEL_ORDER",proof),false);
    }

    /** 原退款/实际恢复流水证明；只投影已提交的累计恢复数量，不接受调用者传入差额。 */
    public void synchronizeRefund(Long afterSaleId) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("退款缓存同步必须在事务外");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || afterSaleId==null || afterSaleId<=0 || afterSaleId>9007199254740991L)throw exception0(403,"需要原租户售后号");
        SeckillRefundRecordDO refund=refunds.restoredProof(tenant,afterSaleId);
        if(refund==null)throw exception0(503,"原退款恢复流水未确认");
        if(refunds.historicalReturn(tenant,afterSaleId)!=0)throw exception0(409,"清算后退款已归还普通库存，不允许回补原活动缓存");
        SeckillRequestDO proof=resources.cacheProof(tenant,refund.getRequestNo());
        if(proof==null || !"PAID".equals(proof.getResourceState()) || !"SYNCED".equals(proof.getResourceCacheState())
                || !Objects.equals(proof.getOrderId(),refund.getOrderId()) || !Objects.equals(proof.getMemberId(),refund.getMemberId())
                || !Objects.equals(proof.getActivityId(),refund.getActivityId()) || !Objects.equals(proof.getGeneration(),refund.getGeneration())
                || !Objects.equals(proof.getRedisEpoch(),refund.getRedisEpoch()) || !Objects.equals(proof.getSkuId(),refund.getSkuId())
                || refund.getRestoredTotal()==null || refund.getRestoredTotal()<refund.getRestoreCount() || refund.getRestoredTotal()>proof.getQuantity())
            throw exception0(503,"原退款恢复与付款资源不一致");
        SeckillAdmissionIntentDO original=intents.get(tenant,proof.getMemberId(),proof.getClientRequestId());
        if(original==null || !Objects.equals(original.getRequestNo(),proof.getRequestNo()) || !Objects.equals(original.getRequestDigest(),proof.getRequestDigest()))
            throw exception0(503,"原退款恢复与受理意图不一致");
        requireSuccess(execute(original,"REFUND_PAID",proof,refund.getRestoredTotal()),false);
    }

    private Long execute(SeckillAdmissionIntentDO original,String mode) {
        return execute(original,mode,null);
    }
    private Long execute(SeckillAdmissionIntentDO original,String mode,SeckillRequestDO closedProof) {
        return execute(original,mode,closedProof,null);
    }
    private Long execute(SeckillAdmissionIntentDO original,String mode,SeckillRequestDO closedProof,Integer restoredTotal) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Redis预占必须在数据库事务外");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || original==null
                || !Objects.equals(tenant,original.getTenantId())) throw exception0(403,"预占需要可信当前租户");
        SeckillAdmissionIntentDO current=intents.get(tenant,original.getMemberId(),original.getClientRequestId());
        if (current==null || !Objects.equals(current.getRequestNo(),original.getRequestNo())
                || !Objects.equals(current.getContextDigest(),original.getContextDigest()) || !Objects.equals(current.getRequestDigest(),original.getRequestDigest()))
            throw exception0(503,"原受理意图尚未持久确认，不能操作Redis");
        int expected=mode.equals("RESERVE")?0:mode.equals("RELEASE")?20:10;
        if (!Integer.valueOf(expected).equals(current.getStatus())) throw exception0(409,"原持久裁决不允许此次预占操作");
        SeckillRequestDO request=SeckillAdmissionIntentService.frozen(current);
        if (mode.equals("RELEASE") && admissions.ownRequest(tenant,request.getMemberId(),request.getRequestNo())!=null)
            throw exception0(503,"原持久申请已存在，不能按未受理拒绝释放");
        if (mode.equals("CONFIRM") || mode.equals("CLOSE_ACCEPTED") || mode.equals("PAY_ACCEPTED") || mode.equals("CANCEL_ORDER") || mode.equals("REFUND_PAID")) {
            SeckillRequestDO accepted=admissions.ownRequest(tenant,request.getMemberId(),request.getRequestNo());
            if (!sameReservation(accepted,request))
                throw exception0(503,"持久申请尚未确认，不能确认缓存预占");
            if (mode.equals("CONFIRM") && !Arrays.asList(10,20,30,60).contains(accepted.getStatus()))
                throw exception0(409,"原申请已关闭，不能迟到确认缓存预占");
            if (mode.equals("CLOSE_ACCEPTED") && (closedProof==null || !sameReservation(accepted,closedProof)
                    || !Integer.valueOf(50).equals(accepted.getStatus()) || accepted.getOrderId()!=null
                    || accepted.getVersion()<closedProof.getVersion() || accepted.getFenceVersion()<closedProof.getFenceVersion()))
                throw exception0(503,"原已受理申请未永久关闭，不能释放缓存");
            if (mode.equals("PAY_ACCEPTED") || mode.equals("CANCEL_ORDER") || mode.equals("REFUND_PAID")) {
                String terminal=mode.equals("CANCEL_ORDER")?"CANCELLED":"PAID";
                if (closedProof==null || !sameReservation(accepted,closedProof) || !terminal.equals(accepted.getResourceState())
                        || !Objects.equals(accepted.getOrderId(),closedProof.getOrderId()) || accepted.getOrderId()==null
                        || !Objects.equals(accepted.getResourceOrderVersion(),closedProof.getResourceOrderVersion())
                        || !Objects.equals(accepted.getResourcePayOrderId(),closedProof.getResourcePayOrderId())
                        || !Objects.equals(accepted.getResourceTime(),closedProof.getResourceTime())
                        || mode.equals("CANCEL_ORDER") && !Integer.valueOf(50).equals(accepted.getStatus()))
                    throw exception0(503,"原订单资源终局变化，不能同步缓存");
            }
        }
        SeckillRedisEpochDO epoch=epochs.initial(tenant,request.getActivityId(),request.getGeneration());
        if (epoch==null || !Arrays.asList(20,30).contains(epoch.getStatus()) || !Objects.equals(epoch.getInitToken(),request.getInitToken())
                || !Objects.equals(epoch.getEpoch(),request.getRedisEpoch()) || epoch.getInitDeadline()==null) throw exception0(503,"原预占epoch不完整，不能重建库存");
        if (mode.equals("RESERVE") && !Integer.valueOf(20).equals(epoch.getStatus())) throw exception0(409,"原epoch已停止新预占");
        manifests.expectedPausedSeed(epoch); // 验证原持久定义/摘要，不访问Redis或填充余额。
        SeckillPublishedManifest manifest=JsonUtils.parseObject(epoch.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        SeckillPublishedManifest.Sku sku=manifest.getSkus().stream().filter(x->Objects.equals(x.getSkuId(),request.getSkuId())).findFirst().orElseThrow(()->exception0(503,"原活动没有该规格"));
        if (!Objects.equals(sku.getPriceCent(),request.getPriceCent()) || !Objects.equals(request.getReservationToken(),request.getRequestNo())
                || request.getQuoteExpiresTime()==null || request.getQuantity()==null || request.getQuantity()<1 || request.getQuantity()>manifest.getSingleLimit())
            throw exception0(503,"原预占数量或价格不一致");
        String key=prefix+":seckill:{"+tenant+":"+request.getActivityId()+":"+request.getGeneration()+"}:epoch:1:runtime";
        List<String> arguments=new ArrayList<>(Arrays.asList(mode,epoch.getInitToken(),
                Long.toString(epoch.getInitDeadline().toInstant(ZoneOffset.UTC).toEpochMilli()),epoch.getDefinitionDigest(),manifest.getStartAt().toString(),manifest.getEndAt().toString(),
                manifest.getSingleLimit().toString(),manifest.getMemberLimit().toString(),Integer.toString(limits.getMaxMembers()),Integer.toString(limits.getMaxRequests()),
                request.getRequestNo(),request.getMemberId().toString(),request.getSkuId().toString(),request.getQuantity().toString(),request.getPriceCent().toString(),
                request.getRequestDigest(),Long.toString(request.getQuoteExpiresTime().toInstant(ZoneOffset.UTC).toEpochMilli()),request.getReservationToken()));
        if(mode.equals("REFUND_PAID")) {
            if(restoredTotal==null || restoredTotal<1 || restoredTotal>request.getQuantity())throw exception0(503,"原退款累计恢复数量不正确");
            arguments.add(restoredTotal.toString());
        }
        return redis.execute(SCRIPT,Collections.singletonList(key),arguments.toArray());
    }
    private boolean sameReservation(SeckillRequestDO a,SeckillRequestDO b) {
        return a!=null && b!=null && Objects.equals(a.getTenantId(),b.getTenantId()) && Objects.equals(a.getMemberId(),b.getMemberId())
                && Objects.equals(a.getRequestNo(),b.getRequestNo()) && Objects.equals(a.getClientRequestId(),b.getClientRequestId())
                && Objects.equals(a.getRequestDigest(),b.getRequestDigest()) && Objects.equals(a.getActivityId(),b.getActivityId())
                && Objects.equals(a.getGeneration(),b.getGeneration()) && Objects.equals(a.getActivityVersion(),b.getActivityVersion())
                && Objects.equals(a.getRedisEpoch(),b.getRedisEpoch()) && Objects.equals(a.getInitToken(),b.getInitToken())
                && Objects.equals(a.getSkuId(),b.getSkuId()) && Objects.equals(a.getQuantity(),b.getQuantity())
                && Objects.equals(a.getPriceCent(),b.getPriceCent()) && Objects.equals(a.getReservationToken(),b.getReservationToken())
                && Objects.equals(a.getQuoteId(),b.getQuoteId()) && Objects.equals(a.getQuoteDigest(),b.getQuoteDigest())
                && Objects.equals(a.getQuoteExpiresTime(),b.getQuoteExpiresTime());
    }
    private void requireSuccess(Long result,boolean reserve) {
        if (reserve && Long.valueOf(-5).equals(result)) throw exception0(429,"本场待处理申请较多，请稍后再试");
        if (reserve && Arrays.asList(-3L,-6L,-7L,-9L).contains(result)) throw exception0(409,"本次预占的活动、库存、限额或原请求已不可用");
        if (result==null || result!=1L && result!=2L) throw exception0(503,"原预占未同步，请核对持久裁决；不能因超时释放或重建库存");
    }
}
