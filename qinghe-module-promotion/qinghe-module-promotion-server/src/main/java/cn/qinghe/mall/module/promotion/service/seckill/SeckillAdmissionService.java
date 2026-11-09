package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.config.SeckillAdmissionProperties;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillAdmissionRedisDAO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.framework.member.CouponMemberAccess;
import cn.qinghe.mall.module.promotion.framework.product.CouponProductScopeApi;
import cn.qinghe.mall.module.promotion.framework.trade.SeckillQuoteBindingClient;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillAdmissionAttemptDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillQuoteBindingRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import lombok.extern.slf4j.Slf4j;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 原报价绑定→持久意图→Redis保护→本地受理事务。失败先取得不可逆DB裁决，再关闭trade、释放缓存。
 * 消费者HTTP委托本服务；不直接创建订单、调用支付或发送MQ。核心库存/个人额度/原申请/流水/Outbox同事务。
 */
@Service @Slf4j
public class SeckillAdmissionService {
    private final CouponMemberAccess access;
    private final SeckillQuoteBindingClient trade;
    private final SeckillAdmissionIntentService preparation;
    private final SeckillAdmissionIntentMapper intents;
    private final SeckillIntentRecoveryMapper recovery;
    private final SeckillAdmissionMapper admissions;
    private final SeckillActivityMapper activities;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillAdmissionRedisDAO redis;
    private final SeckillAdmissionProperties limits;
    private final CouponProductScopeApi products;
    private final SecurityProperties security;
    private final Semaphore calls=new Semaphore(8);
    private final TransactionTemplate write;
    public SeckillAdmissionService(CouponMemberAccess access,SeckillQuoteBindingClient trade,SeckillAdmissionIntentService preparation,
            SeckillAdmissionIntentMapper intents,SeckillIntentRecoveryMapper recovery,SeckillAdmissionMapper admissions,SeckillActivityMapper activities,SeckillRedisEpochMapper epochs,
            SeckillAdmissionRedisDAO redis,SeckillAdmissionProperties limits,CouponProductScopeApi products,SecurityProperties security,
            PlatformTransactionManager manager,@Value("${qinghe.promotion.product-base-url:http://127.0.0.1:8082}") String origin) {
        URI uri=URI.create(origin);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())))
            throw new IllegalArgumentException("秒杀商品来源必须为固定可信origin");
        this.access=access;this.trade=trade;this.preparation=preparation;this.intents=intents;this.recovery=recovery;this.admissions=admissions;this.activities=activities;
        this.epochs=epochs;this.redis=redis;this.limits=limits;this.products=products;this.security=security;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public SeckillAdmissionAttemptDTO attempt(HttpServletRequest request,String store,String client,String quote,String quoteDigest) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("受理协调不能嵌套数据库事务");
        MemberStorefrontRespDTO entry=access.check(request,store);
        SeckillAdmissionIntentDO intent=preparation.original(client,quote,quoteDigest);
        if (intent!=null && !Integer.valueOf(0).equals(intent.getStatus())) return finish(intent);
        if (intent==null) {
            if (!Boolean.TRUE.equals(entry.getCanPurchase())) throw exception0(403,"店铺暂不支持购买");
            SeckillQuoteBindingRespDTO bound=trade.bind(client,quote,quoteDigest);
            if (!Integer.valueOf(0).equals(bound.getState())) throw exception0(409,"原报价已关闭或已建单，请查询原申请");
            intent=preparation.prepare(bound); // 未确认此事务提交前，绝不调用Redis预占。
        }
        if (!Integer.valueOf(0).equals(intent.getStatus())) return finish(intent);
        SeckillAdmissionIntentDO decision;
        try {
            if (!Boolean.TRUE.equals(entry.getCanPurchase())) throw exception0(409,"店铺已停止新购买");
            validateProduct(request,intent);
            redis.reserve(intent);
            // 原报价绑定等RPC可能耗时；持久受理前再读取当前店铺经营资格，不沿用很早的入口快照。
            if (!Boolean.TRUE.equals(access.check(request,store).getCanPurchase())) throw exception0(409,"店铺已停止新购买");
            decision=accept(intent);
        } catch (RuntimeException uncertain) {
            Integer code=503;if (uncertain instanceof ServiceException) code=((ServiceException)uncertain).getCode();
            if (!Arrays.asList(400,409,429).contains(code)) code=503;
            // 本地提交响应丢失亦走同父锁裁决：若实际已提交，返回10；裁决仍未知则抛出，绝不释放。
            decision=preparation.reject(intent.getClientRequestId(),intent.getRequestNo(),intent.getRequestDigest(),code);
        }
        return finish(decision);
    }
    private SeckillAdmissionIntentDO accept(SeckillAdmissionIntentDO original) {
        SeckillRequestDO frozen=SeckillAdmissionIntentService.frozen(original);LoginUser actor=access.actor();
        return write.execute(tx -> {
            SeckillActivityDO activity=activities.lock(actor.getTenantId(),frozen.getActivityId());
            if (activity==null) throw exception0(409,"原活动已不可受理");
            SeckillActivitySkuDO sku=admissions.lockPool(actor.getTenantId(),activity.getId(),frozen.getSkuId());
            SeckillMemberQuotaDO quota=admissions.lockQuota(actor.getTenantId(),activity.getId(),frozen.getGeneration(),actor.getId());
            SeckillAdmissionIntentDO intent=intents.lock(actor.getTenantId(),original.getRequestNo());
            if (intent==null || !Objects.equals(intent.getMemberId(),actor.getId()) || !Objects.equals(intent.getContextDigest(),original.getContextDigest()))
                throw exception0(409,"原受理意图已变化");
            if (!Integer.valueOf(0).equals(intent.getStatus())) return intent;
            SeckillRequestDO row=SeckillAdmissionIntentService.frozen(intent);
            SeckillRedisEpochDO epoch=epochs.initial(actor.getTenantId(),activity.getId(),row.getGeneration());
            LocalDateTime now=admissions.now();
            if (!Integer.valueOf(20).equals(activity.getStatus()) || !Objects.equals(activity.getGeneration(),row.getGeneration())
                    || !Objects.equals(activity.getVersion(),row.getActivityVersion()) || !Objects.equals(activity.getRedisEpoch(),row.getRedisEpoch())
                    || epoch==null || !Integer.valueOf(20).equals(epoch.getStatus()) || !Objects.equals(epoch.getInitToken(),row.getInitToken())
                    || !Objects.equals(epoch.getPublishRequestId(),activity.getPublishOperationNo()) || sku==null || quota==null
                    || !Integer.valueOf(20).equals(sku.getAllocationStatus()) || !Objects.equals(sku.getPriceCent(),row.getPriceCent())
                    || now.isBefore(activity.getStartTime()) || !now.isBefore(activity.getEndTime()) || !now.isBefore(row.getQuoteExpiresTime())
                    || row.getQuantity()>activity.getSingleLimit() || row.getQuantity()>activity.getMemberLimit()
                    || sku.getAvailableQuantity()-sku.getReturnPendingQuantity()<row.getQuantity()
                    || (long)quota.getHeldQuantity()+quota.getPaidQuantity()+row.getQuantity()>activity.getMemberLimit())
                throw exception0(409,"活动、库存、限额或原报价已不可受理");
            if (admissions.pendingEvents(actor.getTenantId(),limits.getMaxPendingEvents())>=limits.getMaxPendingEvents()) throw exception0(429,"待处理申请较多，请稍后再试");
            if (admissions.holdQuota(actor.getTenantId(),activity.getId(),row.getGeneration(),actor.getId(),quota.getVersion(),row.getQuantity(),activity.getMemberLimit())!=1
                    || admissions.holdPool(actor.getTenantId(),activity.getId(),row.getGeneration(),row.getRedisEpoch(),row.getInitToken(),row.getActivityVersion(),
                        row.getSkuId(),sku.getVersion(),row.getQuantity(),row.getQuoteExpiresTime())!=1) throw exception0(409,"原额度条件已变化");
            row.setAcceptedTime(now);row.setReconcileDeadline(now.plusSeconds(120));row.setPayDeadline(now.plusMinutes(30));
            String event=UUID.randomUUID().toString();
            SeckillOutboxDO outbox=new SeckillOutboxDO();outbox.setTenantId(actor.getTenantId());outbox.setEventId(event);
            outbox.setRequestNo(row.getRequestNo());outbox.setAvailableAt(now);
            Map<String,Object> payload=new LinkedHashMap<>();payload.put("schemaVersion",1);payload.put("tenantId",actor.getTenantId());
            payload.put("requestNo",row.getRequestNo());payload.put("aggregateVersion",0);outbox.setPayload(JsonUtils.toJsonString(payload));
            if (admissions.insertRequest(row)!=1 || admissions.journalAccepted(row,event)!=1 || admissions.insertCreateEvent(outbox)!=1
                    || intents.accepted(actor.getTenantId(),row.getRequestNo(),intent.getVersion())!=1) throw new IllegalStateException("原申请受理事务未完整写入");
            return intents.lock(actor.getTenantId(),row.getRequestNo());
        });
    }
    private SeckillAdmissionAttemptDTO finish(SeckillAdmissionIntentDO decision) {
        if (decision==null || !Arrays.asList(10,20).contains(decision.getStatus())) throw exception0(503,"原申请裁决待确认");
        LoginUser actor=access.actor();
        if (!Objects.equals(actor.getTenantId(),decision.getTenantId()) || !Objects.equals(actor.getId(),decision.getMemberId())) throw exception0(403,"原申请不属于当前会员");
        SeckillAdmissionAttemptDTO out=new SeckillAdmissionAttemptDTO();out.setDecision(decision.getStatus());out.setFailureCode(decision.getFailureCode());
        out.setRequestNo(decision.getRequestNo());out.setClientRequestId(decision.getClientRequestId());out.setRedisSynchronized(false);
        if (Integer.valueOf(10).equals(decision.getStatus())) {
            SeckillRequestDO row=admissions.ownRequest(actor.getTenantId(),actor.getId(),decision.getRequestNo());
            if (row==null || !Objects.equals(row.getRequestDigest(),decision.getRequestDigest())) throw exception0(503,"原受理记录待核对");
            out.setRequest(SeckillRequestReceiptService.project(row));
            try { redis.confirm(decision);out.setRedisSynchronized(true);cacheAcknowledged(decision); }
            catch (RuntimeException pending) { log.warn("Seckill admission committed, Redis confirmation pending tenant={},request={}",actor.getTenantId(),decision.getRequestNo()); }
        } else {
            SeckillRequestDO row=SeckillAdmissionIntentService.frozen(decision);out.setQuoteClosed(false);
            try {
                SeckillQuoteBindingRespDTO closed=trade.close(row.getClientRequestId(),row.getQuoteId(),row.getQuoteDigest());
                if (Integer.valueOf(20).equals(closed.getState())) {
                    out.setQuoteClosed(true);redis.releaseRejected(decision,closed);out.setRedisSynchronized(true);cacheAcknowledged(decision);
                } else { log.error("Rejected seckill admission has an order-side result tenant={},request={},order={}",actor.getTenantId(),decision.getRequestNo(),closed.getOrderId()); }
            } catch (RuntimeException pending) { log.warn("Seckill admission rejected, quote close or Redis release pending tenant={},request={}",actor.getTenantId(),decision.getRequestNo()); }
        }
        return out;
    }
    /** Redis已经确认；单独记进度失败仍保留原成功事实和待恢复记录，不重置恢复预算。 */
    private void cacheAcknowledged(SeckillAdmissionIntentDO decision) {
        try {
            Integer count=write.execute(tx -> recovery.synced(decision));
            if (!Integer.valueOf(1).equals(count)) {
                SeckillAdmissionIntentDO current=intents.get(decision.getTenantId(),decision.getMemberId(),decision.getClientRequestId());
                if (current==null || !"SYNCED".equals(current.getCacheSyncState())) throw new IllegalStateException("原缓存同步进度待核对");
            }
        } catch (RuntimeException pending) {
            log.warn("Seckill cache confirmed, progress acknowledgement pending tenant={},request={}",decision.getTenantId(),decision.getRequestNo());
        }
    }
    private void validateProduct(HttpServletRequest request,SeckillAdmissionIntentDO intent) {
        SeckillRequestDO row=SeckillAdmissionIntentService.frozen(intent);
        SeckillRedisEpochDO epoch=epochs.initial(row.getTenantId(),row.getActivityId(),row.getGeneration());
        if (epoch==null) throw exception0(503,"原发布定义缺失");
        SeckillPublishedManifest manifest=JsonUtils.parseObject(epoch.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        SeckillPublishedManifest.Sku expected=manifest.getSkus().stream().filter(x->Objects.equals(x.getSkuId(),row.getSkuId())).findFirst().orElseThrow(()->exception0(409,"原规格已失效"));
        String token=SecurityFrameworkUtils.obtainAuthorization(request,security.getTokenHeader());
        if (token==null || token.length()>128) throw exception0(401,"请重新登录");
        if (!calls.tryAcquire()) throw exception0(503,"秒杀商品校验繁忙");
        try {
            CommonResult<List<ProductTradeSkuRespDTO>> response=products.sales("Bearer "+token,Collections.singletonList(row.getSkuId()));
            if (response==null || response.getCode()==null) throw exception0(503,"商品资格结果不完整");response.checkError();
            List<ProductTradeSkuRespDTO> rows=response.getData();if (rows==null || rows.size()>1) throw exception0(503,"商品资格结果不完整");
            if (rows.isEmpty()) throw exception0(409,"原活动商品已下架或停用");ProductTradeSkuRespDTO product=rows.get(0);
            if (product==null || !Objects.equals(product.getTenantId(),row.getTenantId()) || !Objects.equals(product.getSkuId(),row.getSkuId())
                    || !Objects.equals(product.getSpuId(),manifest.getProductSpuId()) || !Objects.equals(product.getSpuVersion(),manifest.getProductSpuVersion())
                    || !Objects.equals(product.getSkuVersion(),expected.getSkuVersion())) throw exception0(409,"原活动商品资料已变化");
        } finally { calls.release(); }
    }
}
