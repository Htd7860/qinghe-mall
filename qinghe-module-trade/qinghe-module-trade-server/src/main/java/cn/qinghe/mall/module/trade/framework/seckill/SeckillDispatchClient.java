package cn.qinghe.mall.module.trade.framework.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.framework.order.OrderCouponFeignApi;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 后台接收端先核对来源库原事件/受理快照；固定来源/现有trade→promotion凭据，无新服务会话。 */
@Service
public class SeckillDispatchClient {
    private final OrderCouponFeignApi api;
    private final OrderResourceProperties properties;
    private final Semaphore calls=new Semaphore(8);
    public SeckillDispatchClient(OrderCouponFeignApi api,OrderResourceProperties properties,
            @Value("${qinghe.trade.promotion-base-url:http://127.0.0.1:8085}") String origin) {
        URI uri=URI.create(origin);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())))
            throw new IllegalArgumentException("秒杀事件来源必须为固定可信origin");
        this.api=api;this.properties=properties;
    }
    public SeckillDispatchSnapshotDTO verify(String brokerBody) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("秒杀来源核对只能在事务外可信后台执行");
        SeckillEventDTO alleged=SeckillEventEnvelope.decode(brokerBody);Long tenant=TenantContextHolder.getTenantId();
        if (!Arrays.asList("SECKILL_CREATE","SECKILL_CLOSE").contains(alleged.getEventType())) throw exception0(400,"结果事件不能作为建单命令");
        if (!id(tenant) || TenantContextHolder.isIgnore() || !Objects.equals(tenant,alleged.getTenantId())) throw exception0(403,"事件租户上下文不正确");
        String token=properties.getPromotionToken();
        if (token==null || !token.matches("[A-Za-z0-9_-]{43,128}")) throw exception0(503,"来源事件读取服务未配置");
        if (!calls.tryAcquire()) throw exception0(503,"来源事件读取繁忙");
        try {
            SeckillEventLookupReqDTO query=new SeckillEventLookupReqDTO();query.setEventId(alleged.getEventId());
            CommonResult<SeckillDispatchSnapshotDTO> response;
            try { response=api.seckillEvent("Bearer "+token,tenant,query); }
            catch (RuntimeException unknown) { throw exception0(503,"原秒杀事件尚未核对，请按原事件恢复"); }
            if (response==null || response.getCode()==null) throw incomplete();response.checkError();SeckillDispatchSnapshotDTO verified=response.getData();
            if (verified==null || verified.getEvent()==null || !brokerBody.equals(SeckillEventEnvelope.encode(verified.getEvent())))
                throw exception0(409,"秒杀消息与来源库原事件不一致");
            validate(verified.getRequest(),verified.getEvent());return verified;
        } finally { calls.release(); }
    }
    public SeckillResourceReceiptDTO resource(SeckillResourceLookupDTO q) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("资源证明只允许事务外可信后台读取");
        Long tenant=TenantContextHolder.getTenantId();
        if(!id(tenant) || TenantContextHolder.isIgnore() || q==null || !id(q.getMemberId()) || !id(q.getOrderId())
                || !uuid(q.getRequestNo()) || !digest(q.getRequestDigest()))throw exception0(403,"需要原租户订单引用");
        String token=properties.getPromotionToken();
        if(token==null || !token.matches("[A-Za-z0-9_-]{43,128}"))throw exception0(503,"资源读取凭据未配置");
        if(!calls.tryAcquire())throw exception0(503,"原资源证明读取繁忙");
        try {
            CommonResult<SeckillResourceReceiptDTO> response;
            try {response=api.seckillResource("Bearer "+token,tenant,q);}
            catch(RuntimeException unknown){throw exception0(503,"原资源证明尚未核对");}
            if(response==null || response.getCode()==null)throw incomplete();response.checkError();SeckillResourceReceiptDTO out=response.getData();
            if(out==null)return null; // 只是尚无终局，不能把它解释为已释放。
            if(!Objects.equals(out.getTenantId(),tenant) || !Objects.equals(out.getRequestNo(),q.getRequestNo())
                    || !Objects.equals(out.getMemberId(),q.getMemberId()) || !Objects.equals(out.getOrderId(),q.getOrderId())
                    || !Objects.equals(out.getRequestDigest(),q.getRequestDigest()) || !Arrays.asList("PAID","CANCELLED").contains(out.getState())
                    || !Arrays.asList("PENDING","SYNCED").contains(out.getCacheState()) || !version(out.getOrderVersion())
                    || !version(out.getRequestVersion()) || !id(out.getPayOrderId()) || out.getResourceTime()==null
                    || out.getResourceTime().getYear()<2000 || out.getResourceTime().getYear()>2099)throw incomplete();
            return out;
        } finally {calls.release();}
    }
    private void validate(SeckillAcceptedRequestDTO r,SeckillEventDTO event) {
        if (r==null || !Objects.equals(r.getTenantId(),event.getTenantId()) || !Objects.equals(r.getRequestNo(),event.getRequestNo())
                || !id(r.getMemberId()) || !uuid(r.getClientRequestId()) || !digest(r.getRequestDigest()) || !id(r.getActivityId())
                || r.getGeneration()==null || r.getGeneration()<1 || !version(r.getActivityVersion()) || !Long.valueOf(1).equals(r.getRedisEpoch())
                || !id(r.getSkuId()) || r.getQuantity()==null || r.getQuantity()<1 || r.getQuantity()>9999
                || r.getPriceCent()==null || r.getPriceCent()<1 || r.getPriceCent()>2000000000
                || !money(r.getGoodsAmount()) || !money(r.getShippingAmount()) || !money(r.getPayAmount())
                || r.getGoodsAmount()!=((long)r.getPriceCent())*r.getQuantity() || r.getPayAmount()!=r.getGoodsAmount()+r.getShippingAmount()
                || !Objects.equals(r.getQuoteId(),r.getRequestNo()) || !digest(r.getQuoteDigest())
                || !Arrays.asList(10,20,30,40,50,60).contains(r.getStatus()) || !version(r.getVersion()) || r.getVersion()<event.getAggregateVersion()
                || !version(r.getFenceVersion()) || r.getOrderId()!=null && !id(r.getOrderId())
                || Integer.valueOf(20).equals(r.getStatus()) && r.getOrderId()==null || Integer.valueOf(10).equals(r.getStatus()) && r.getOrderId()!=null
                || r.getQuoteExpiresAt()==null || r.getAcceptedAt()==null || r.getReconcileDeadline()==null || r.getPayDeadline()==null
                || !r.getAcceptedAt().isBefore(r.getQuoteExpiresAt())
                || !r.getReconcileDeadline().toInstant().equals(r.getAcceptedAt().plusSeconds(120).toInstant())
                || !r.getPayDeadline().toInstant().equals(r.getAcceptedAt().plusMinutes(30).toInstant())) throw incomplete();
        String original=DigestUtil.sha256Hex("seckill-accept-v1:"+r.getTenantId()+":"+r.getMemberId()+":"+r.getClientRequestId()+":"+r.getQuoteId()+":"+r.getQuoteDigest());
        if (!original.equals(r.getRequestDigest())) throw exception0(409,"原受理申请摘要不匹配");
    }
    private boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
    private boolean money(Long x) { return x!=null && x>=0 && x<=9007199254740991L; }
    private boolean version(Long x) { return money(x); }
    private boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private boolean digest(String x) { return x!=null && x.matches("[0-9a-f]{64}"); }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原秒杀受理快照不完整，请核查原申请"); }
}
