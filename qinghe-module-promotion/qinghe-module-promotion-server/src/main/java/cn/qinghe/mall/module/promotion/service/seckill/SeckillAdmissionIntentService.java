package cn.qinghe.mall.module.promotion.service.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.member.CouponMemberAccess;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillQuoteBindingRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** Redis之前先固化原意图；只准备或拒绝，不扣池/个人额度，不报告已受理。 */
@Service
public class SeckillAdmissionIntentService {
    private final CouponMemberAccess access;
    private final SeckillAdmissionIntentMapper intents;
    private final SeckillActivityMapper activities;
    private final SeckillAdmissionMapper admissions;
    private final SeckillRedisEpochMapper epochs;
    private final TransactionTemplate write;
    public SeckillAdmissionIntentService(CouponMemberAccess access,SeckillAdmissionIntentMapper intents,SeckillActivityMapper activities,
            SeckillAdmissionMapper admissions,SeckillRedisEpochMapper epochs,PlatformTransactionManager manager) {
        this.access=access;this.intents=intents;this.activities=activities;this.admissions=admissions;this.epochs=epochs;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    /** 仅供协调器传入已由SeckillQuoteBindingClient校验的真实绑定回执，禁止直接接浏览器DTO。 */
    public SeckillAdmissionIntentDO prepare(SeckillQuoteBindingRespDTO bound) {
        outside();LoginUser actor=access.actor();
        if (bound==null || !Integer.valueOf(0).equals(bound.getState()) || !Objects.equals(actor.getTenantId(),bound.getTenantId())
                || !Objects.equals(actor.getId(),bound.getMemberId()) || !Objects.equals(bound.getRequestNo(),bound.getQuoteId())
                || !uuid(bound.getRequestNo()) || !uuid(bound.getClientRequestId()) || bound.getActivityId()==null || bound.getSkuId()==null
                || bound.getGeneration()==null || bound.getQuantity()==null || bound.getPriceCent()==null || bound.getQuoteExpiresAt()==null
                || bound.getQuoteDigest()==null || !bound.getQuoteDigest().matches("[0-9a-f]{64}")) throw exception0(400,"需要原会员的真实报价绑定回执");
        String digest=DigestUtil.sha256Hex("seckill-accept-v1:"+actor.getTenantId()+":"+actor.getId()+":"+bound.getClientRequestId()+":"+bound.getQuoteId()+":"+bound.getQuoteDigest());
        if (!Objects.equals(digest,bound.getRequestDigest())) throw exception0(409,"原申请摘要不匹配");
        SeckillAdmissionIntentDO existing=intents.get(actor.getTenantId(),actor.getId(),bound.getClientRequestId());
        if (existing!=null) { same(existing,bound.getRequestNo(),digest);return existing; }
        return write.execute(tx -> {
            access.actor();SeckillActivityDO activity=activities.lock(actor.getTenantId(),bound.getActivityId());
            if (activity==null) throw exception0(409,"原活动已不可受理");
            SeckillActivitySkuDO sku=admissions.lockPool(actor.getTenantId(),activity.getId(),bound.getSkuId());
            admissions.ensureQuota(actor.getTenantId(),activity.getId(),bound.getGeneration(),actor.getId());
            admissions.lockQuota(actor.getTenantId(),activity.getId(),bound.getGeneration(),actor.getId());
            SeckillAdmissionIntentDO previous=intents.lock(actor.getTenantId(),bound.getRequestNo());
            if (previous!=null) { same(previous,bound.getRequestNo(),digest);return previous; }
            SeckillRedisEpochDO epoch=epochs.initial(actor.getTenantId(),activity.getId(),bound.getGeneration());
            LocalDateTime now=admissions.now(),expires=LocalDateTime.ofInstant(bound.getQuoteExpiresAt().toInstant(),ZoneOffset.UTC);
            if (!Integer.valueOf(20).equals(activity.getStatus()) || !Objects.equals(activity.getGeneration(),bound.getGeneration())
                    || !Objects.equals(activity.getVersion(),bound.getActivityVersion()) || !Long.valueOf(1).equals(bound.getRedisEpoch())
                    || !Objects.equals(activity.getRedisEpoch(),bound.getRedisEpoch()) || epoch==null || !Integer.valueOf(20).equals(epoch.getStatus())
                    || !uuid(epoch.getInitToken()) || !Objects.equals(epoch.getPublishRequestId(),activity.getPublishOperationNo())
                    || sku==null || !Integer.valueOf(20).equals(sku.getAllocationStatus()) || !Objects.equals(sku.getPriceCent(),bound.getPriceCent())
                    || bound.getQuantity()<1 || bound.getQuantity()>activity.getSingleLimit() || bound.getQuantity()>activity.getMemberLimit()
                    || now.isBefore(activity.getStartTime()) || !now.isBefore(activity.getEndTime()) || !now.isBefore(expires)
                    || !money(bound.getGoodsAmount()) || bound.getGoodsAmount()!=((long)bound.getPriceCent())*bound.getQuantity()
                    || !money(bound.getShippingAmount()) || !money(bound.getPayAmount())
                    || bound.getPayAmount()!=bound.getGoodsAmount()+bound.getShippingAmount() || bound.getPayAmount()>9007199254740991L)
                throw exception0(409,"原报价、活动状态或期限已变化");
            SeckillRequestDO request=new SeckillRequestDO();request.setTenantId(actor.getTenantId());request.setMemberId(actor.getId());
            request.setRequestNo(bound.getRequestNo());request.setClientRequestId(bound.getClientRequestId());request.setRequestDigest(digest);
            request.setActivityId(activity.getId());request.setGeneration(bound.getGeneration());request.setActivityVersion(activity.getVersion());
            request.setRedisEpoch(epoch.getEpoch());request.setInitToken(epoch.getInitToken());request.setSkuId(sku.getSkuId());request.setQuantity(bound.getQuantity());
            request.setPriceCent(bound.getPriceCent());request.setGoodsAmount(bound.getGoodsAmount());request.setShippingAmount(bound.getShippingAmount());
            request.setPayAmount(bound.getPayAmount());request.setQuoteId(bound.getQuoteId());request.setQuoteDigest(bound.getQuoteDigest());
            request.setQuoteExpiresTime(expires);request.setReservationToken(bound.getRequestNo());
            SeckillAdmissionIntentDO row=new SeckillAdmissionIntentDO();row.setTenantId(actor.getTenantId());row.setMemberId(actor.getId());
            row.setRequestNo(request.getRequestNo());row.setClientRequestId(request.getClientRequestId());row.setRequestDigest(digest);
            row.setActivityId(activity.getId());row.setGeneration(activity.getGeneration());row.setSkuId(sku.getSkuId());
            row.setQuoteExpiresAt(expires);row.setRequestSnapshot(JsonUtils.toJsonString(request));row.setContextDigest(DigestUtil.sha256Hex(row.getRequestSnapshot()));
            if (row.getRequestSnapshot().getBytes(StandardCharsets.UTF_8).length>16384) throw exception0(400,"原受理意图超出支持范围");
            if (intents.insert(row)!=1) throw new IllegalStateException("原受理意图写入失败");
            return intents.lock(actor.getTenantId(),request.getRequestNo());
        });
    }
    public SeckillAdmissionIntentDO original(String client,String quote,String quoteDigest) {
        outside();LoginUser actor=access.actor();
        if (!uuid(client) || !uuid(quote) || quoteDigest==null || !quoteDigest.matches("[0-9a-f]{64}")) throw exception0(400,"原申请引用不正确");
        SeckillAdmissionIntentDO row=intents.get(actor.getTenantId(),actor.getId(),client);
        if (row==null) return null;
        String digest=DigestUtil.sha256Hex("seckill-accept-v1:"+actor.getTenantId()+":"+actor.getId()+":"+client+":"+quote+":"+quoteDigest);
        same(row,quote,digest);SeckillRequestDO request=frozen(row);
        if (!Objects.equals(request.getQuoteId(),quote) || !Objects.equals(request.getQuoteDigest(),quoteDigest)) throw exception0(409,"原报价不能更换");return row;
    }
    /** 失败处理必须先完成此持久裁决；已经受理10绝不改成拒绝，未知不得解释为可释放。 */
    public SeckillAdmissionIntentDO reject(String clientRequestId,String requestNo,String requestDigest,Integer failureCode) {
        outside();LoginUser actor=access.actor();
        if (!uuid(clientRequestId) || !uuid(requestNo) || !Arrays.asList(400,409,429,503).contains(failureCode)) throw exception0(400,"拒绝申请参数不正确");
        SeckillAdmissionIntentDO before=intents.get(actor.getTenantId(),actor.getId(),clientRequestId);
        if (before==null) throw exception0(503,"原意图尚未确认，不能释放预占");same(before,requestNo,requestDigest);
        return write.execute(tx -> {
            if (activities.lock(actor.getTenantId(),before.getActivityId())==null) throw exception0(503,"原活动父锁缺失，不能推断失败");
            admissions.lockPool(actor.getTenantId(),before.getActivityId(),before.getSkuId());
            admissions.lockQuota(actor.getTenantId(),before.getActivityId(),before.getGeneration(),actor.getId());
            SeckillAdmissionIntentDO current=intents.lock(actor.getTenantId(),requestNo);same(current,requestNo,requestDigest);
            if (!Integer.valueOf(0).equals(current.getStatus())) return current;
            if (intents.reject(actor.getTenantId(),requestNo,current.getVersion(),failureCode)!=1) throw new IllegalStateException("原意图拒绝裁决失败");
            return intents.lock(actor.getTenantId(),requestNo);
        });
    }
    /** TEXT原样保存的不含地址的引用快照；不对MySQL JSON格式化后的文本直接做摘要。 */
    public static SeckillRequestDO frozen(SeckillAdmissionIntentDO row) {
        if (row==null || row.getRequestSnapshot()==null || !Objects.equals(DigestUtil.sha256Hex(row.getRequestSnapshot()),row.getContextDigest())) throw exception0(503,"原受理意图摘要不完整");
        SeckillRequestDO request=JsonUtils.parseObject(row.getRequestSnapshot(),SeckillRequestDO.class);
        if (request==null || !Objects.equals(request.getTenantId(),row.getTenantId()) || !Objects.equals(request.getMemberId(),row.getMemberId())
                || !Objects.equals(request.getRequestNo(),row.getRequestNo()) || !Objects.equals(request.getClientRequestId(),row.getClientRequestId())
                || !Objects.equals(request.getRequestDigest(),row.getRequestDigest()) || !Objects.equals(request.getActivityId(),row.getActivityId())
                || !Objects.equals(request.getGeneration(),row.getGeneration()) || !Objects.equals(request.getSkuId(),row.getSkuId())
                || !Objects.equals(request.getReservationToken(),row.getRequestNo()) || !Objects.equals(request.getQuoteExpiresTime(),row.getQuoteExpiresAt())) throw exception0(503,"原受理意图归属不一致");
        return request;
    }
    private void same(SeckillAdmissionIntentDO row,String request,String digest) { if (row==null || !Objects.equals(row.getRequestNo(),request) || !Objects.equals(row.getRequestDigest(),digest)) throw exception0(409,"原申请参数不能变更"); }
    private void outside() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("受理意图必须在独立短事务中裁决"); }
    private static boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private static boolean money(Long x) { return x!=null && x>=0 && x<=9007199254740991L; }
}
