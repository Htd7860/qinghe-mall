package cn.qinghe.mall.module.promotion.framework.trade;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillEventDTO;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillAdmissionIntentService;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillRefundCommandContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 后台先向trade读取原Outbox/实际订单证明，不能把MQ结果直接当成释放库存依据。 */
@Service
public class SeckillOrderResultClient {
    private final SeckillQuoteBindingApi api;
    private final String token;
    private final Semaphore calls=new Semaphore(8);
    public SeckillOrderResultClient(SeckillQuoteBindingApi api,@Value("${qinghe.promotion.seckill-binding.service-token:}") String token,
            @Value("${qinghe.promotion.trade-base-url:http://127.0.0.1:8083}") String origin) {
        URI uri=URI.create(origin);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())) || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost()))) throw new IllegalArgumentException("结果来源必须为固定可信origin");
        if (token==null || !token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}")) throw new IllegalArgumentException("结果读取服务凭据格式不正确");
        this.api=api;this.token=token;
    }
    public SeckillOrderResultSnapshotDTO verify(String body) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("结果读取只能在事务外可信后台执行");
        SeckillEventDTO e=SeckillEventEnvelope.decode(body);Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || !Objects.equals(e.getTenantId(),tenant)) throw exception0(403,"结果租户上下文不正确");
        if (!Arrays.asList("SECKILL_ORDER_CREATED","SECKILL_REQUEST_CLOSED").contains(e.getEventType())) throw exception0(400,"建单命令不能作为结果处理");
        if (!token.matches("[A-Za-z0-9_-]{43,128}")) throw exception0(503,"结果读取服务凭据未配置");
        if (!calls.tryAcquire()) throw exception0(503,"原结果核对繁忙");
        try {
            SeckillOrderResultLookupDTO query=new SeckillOrderResultLookupDTO();query.setEventId(e.getEventId());
            CommonResult<SeckillOrderResultSnapshotDTO> reply;
            try { reply=api.orderResult("Bearer "+token,tenant,query); }
            catch (RuntimeException pending) { throw exception0(503,"原建单结果尚未核对，请按原事件恢复"); }
            if (reply==null || reply.getCode()==null) throw incomplete();reply.checkError();
            SeckillOrderResultSnapshotDTO out=reply.getData();
            if (out==null || !Objects.equals(out.getTenantId(),e.getTenantId()) || !Objects.equals(out.getEventId(),e.getEventId())
                    || !Objects.equals(out.getRequestNo(),e.getRequestNo()) || !Objects.equals(out.getEventType(),e.getEventType())
                    || !Objects.equals(out.getAggregateVersion(),e.getAggregateVersion()) || !Objects.equals(out.getSchemaVersion(),e.getSchemaVersion())
                    || !Objects.equals(out.getOccurredAt(),e.getOccurredAt()) || out.getBinding()==null) throw incomplete();
            SeckillQuoteBindingRespDTO b=out.getBinding();int expected="SECKILL_ORDER_CREATED".equals(e.getEventType())?10:20;
            if (!Objects.equals(b.getTenantId(),tenant) || !Objects.equals(b.getRequestNo(),e.getRequestNo())
                    || !Integer.valueOf(expected).equals(b.getState()) || !Objects.equals(b.getFenceVersion(),e.getAggregateVersion())
                    || b.getFenceVersion()<1 || b.getCreatedAt()==null || b.getMemberId()==null || b.getMemberId()<=0 || b.getMemberId()>9007199254740991L
                    || expected==10 && (b.getOrderId()==null || b.getOrderId()<=0 || b.getOrderId()>9007199254740991L
                        || b.getClosedAt()!=null || out.getOrderCreatedAt()==null || out.getPayDeadline()==null
                        || !out.getPayDeadline().toInstant().equals(out.getOrderCreatedAt().plusMinutes(30).toInstant()))
                    || expected==20 && (b.getOrderId()!=null || b.getClosedAt()==null || out.getOrderCreatedAt()!=null || out.getPayDeadline()!=null)) throw incomplete();
            return out;
        } finally { calls.release(); }
    }

    /** 原受理事实与trade当前订单状态对照；只读，付款/取消资源转换由后续业务方法另行裁决。 */
    public SeckillOrderStateDTO state(SeckillRequestDO original) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("订单状态读取只能在事务外可信后台执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || original==null
                || !Objects.equals(original.getTenantId(),tenant) || original.getAcceptedTime()==null || original.getPayDeadline()==null
                || original.getQuoteExpiresTime()==null) throw exception0(403,"需要本租户原持久受理申请");
        if (!token.matches("[A-Za-z0-9_-]{43,128}")) throw exception0(503,"订单状态读取服务凭据未配置");
        SeckillQuoteBindingReqDTO query=new SeckillQuoteBindingReqDTO();query.setMemberId(original.getMemberId());query.setRequestNo(original.getRequestNo());
        query.setClientRequestId(original.getClientRequestId());query.setRequestDigest(original.getRequestDigest());
        query.setQuoteId(original.getQuoteId());query.setQuoteDigest(original.getQuoteDigest());
        if (!calls.tryAcquire()) throw exception0(503,"原订单状态读取繁忙");
        try {
            CommonResult<SeckillOrderStateDTO> reply;
            try { reply=api.orderState("Bearer "+token,tenant,query); }
            catch (RuntimeException pending) { throw exception0(503,"原订单状态尚未核对，请按原申请恢复"); }
            if (reply==null || reply.getCode()==null) throw incomplete();reply.checkError();SeckillOrderStateDTO out=reply.getData();
            if (out==null || out.getBinding()==null) throw incomplete();SeckillQuoteBindingRespDTO b=out.getBinding();
            if (!Objects.equals(b.getTenantId(),tenant) || !Objects.equals(b.getMemberId(),original.getMemberId())
                    || !Objects.equals(b.getRequestNo(),original.getRequestNo()) || !Objects.equals(b.getClientRequestId(),original.getClientRequestId())
                    || !Objects.equals(b.getRequestDigest(),original.getRequestDigest()) || !Objects.equals(b.getQuoteId(),original.getQuoteId())
                    || !Objects.equals(b.getQuoteDigest(),original.getQuoteDigest()) || !Integer.valueOf(10).equals(b.getState())
                    || b.getOrderId()==null || b.getOrderId()<=0 || b.getOrderId()>9007199254740991L
                    || original.getOrderId()!=null && !Objects.equals(original.getOrderId(),b.getOrderId())
                    || b.getFenceVersion()==null || b.getFenceVersion()<1 || b.getFenceVersion()>9007199254740991L
                    || b.getCreatedAt()==null || b.getClosedAt()!=null
                    || !Objects.equals(b.getActivityId(),original.getActivityId()) || !Objects.equals(b.getGeneration(),original.getGeneration())
                    || !Objects.equals(b.getActivityVersion(),original.getActivityVersion()) || !Objects.equals(b.getRedisEpoch(),original.getRedisEpoch())
                    || !Objects.equals(b.getSkuId(),original.getSkuId()) || !Objects.equals(b.getQuantity(),original.getQuantity())
                    || !Objects.equals(b.getPriceCent(),original.getPriceCent()) || !Objects.equals(b.getGoodsAmount(),original.getGoodsAmount())
                    || !Objects.equals(b.getShippingAmount(),original.getShippingAmount()) || !Objects.equals(b.getPayAmount(),original.getPayAmount())
                    || b.getQuoteExpiresAt()==null || !b.getQuoteExpiresAt().toInstant().equals(original.getQuoteExpiresTime().toInstant(ZoneOffset.UTC))
                    || out.getOrderCreatedAt()==null || !out.getOrderCreatedAt().toInstant().equals(original.getAcceptedTime().toInstant(ZoneOffset.UTC))
                    || out.getPayDeadline()==null || !out.getPayDeadline().toInstant().equals(original.getPayDeadline().toInstant(ZoneOffset.UTC))) throw incomplete();
            if (out.getOrderVersion()==null || out.getOrderVersion()<0 || out.getOrderVersion()>9007199254740991L
                    || !Arrays.asList(0,10,20,30,40).contains(out.getOrderStatus()) || out.getPayStatus()==null || out.getCancelResourcesReleased()==null
                    || out.getRefundPrice()==null || out.getRefundPrice()<0 || out.getRefundPrice()>b.getPayAmount()
                    || out.getPayOrderId()!=null && (out.getPayOrderId()<=0 || out.getPayOrderId()>9007199254740991L)) throw incomplete();
            if (Boolean.TRUE.equals(out.getPayStatus())) {
                if (out.getPayOrderId()==null || out.getPayTime()==null || out.getPayTime().toInstant().isBefore(out.getOrderCreatedAt().minusSeconds(1).toInstant())
                        || !out.getPayTime().toInstant().isBefore(out.getPayDeadline().toInstant()) || Integer.valueOf(0).equals(out.getOrderStatus())) throw incomplete();
            } else if (out.getPayTime()!=null || out.getRefundPrice()!=0 || !Arrays.asList(0,40).contains(out.getOrderStatus())
                    || Integer.valueOf(40).equals(out.getOrderStatus()) && (out.getPayOrderId()==null || out.getCancelTime()==null
                        || !Arrays.asList(10,30).contains(out.getCancelType()))) throw incomplete();
            if (Boolean.TRUE.equals(out.getCancelResourcesReleased()) && !Integer.valueOf(40).equals(out.getOrderStatus())) throw incomplete();
            return out;
        } finally { calls.release(); }
    }

    /** 读取已成功的原退款，不接收调用方给出的金额或回补数量。 */
    public SeckillRefundSnapshotDTO refund(SeckillRequestDO original,Long afterSaleId) {
        SeckillRefundCommandContext.requireCaller();
        Long tenant=TenantContextHolder.getTenantId();
        if(!safeId(tenant) || TenantContextHolder.isIgnore() || original==null || !Objects.equals(tenant,original.getTenantId())
                || !safeId(original.getOrderId()) || !safeId(afterSaleId) || original.getQuoteExpiresTime()==null)
            throw exception0(403,"需要本租户原订单及售后号");
        if(!token.matches("[A-Za-z0-9_-]{43,128}"))throw exception0(503,"原退款读取凭据未配置");
        if(!calls.tryAcquire())throw exception0(503,"原退款核对繁忙");
        try {
            SeckillRefundLookupDTO q=new SeckillRefundLookupDTO();q.setOrderId(original.getOrderId());q.setAfterSaleId(afterSaleId);
            CommonResult<SeckillRefundSnapshotDTO> reply;
            try { reply=api.refundState("Bearer "+token,tenant,q); }
            catch(RuntimeException pending){throw exception0(503,"原退款事实尚未核对，请按原售后号恢复");}
            if(reply==null || reply.getCode()==null)throw incomplete();reply.checkError();SeckillRefundSnapshotDTO out=reply.getData();
            if(out==null || out.getBinding()==null || !Integer.valueOf(1).equals(out.getSchemaVersion()))throw incomplete();
            SeckillQuoteBindingRespDTO b=out.getBinding();
            if(!Objects.equals(b.getTenantId(),tenant) || !Objects.equals(b.getMemberId(),original.getMemberId())
                    || !Objects.equals(b.getRequestNo(),original.getRequestNo()) || !Objects.equals(b.getClientRequestId(),original.getClientRequestId())
                    || !Objects.equals(b.getRequestDigest(),original.getRequestDigest()) || !Objects.equals(b.getQuoteId(),original.getQuoteId())
                    || !Objects.equals(b.getQuoteDigest(),original.getQuoteDigest()) || !Objects.equals(b.getOrderId(),original.getOrderId())
                    || !Integer.valueOf(10).equals(b.getState()) || !safeId(b.getFenceVersion()) || b.getCreatedAt()==null || b.getClosedAt()!=null
                    || !Objects.equals(b.getActivityId(),original.getActivityId()) || !Objects.equals(b.getGeneration(),original.getGeneration())
                    || !Objects.equals(b.getActivityVersion(),original.getActivityVersion()) || !Objects.equals(b.getRedisEpoch(),original.getRedisEpoch())
                    || !Objects.equals(b.getSkuId(),original.getSkuId()) || !Objects.equals(b.getQuantity(),original.getQuantity())
                    || !Objects.equals(b.getPriceCent(),original.getPriceCent()) || !Objects.equals(b.getGoodsAmount(),original.getGoodsAmount())
                    || !Objects.equals(b.getShippingAmount(),original.getShippingAmount()) || !Objects.equals(b.getPayAmount(),original.getPayAmount())
                    || b.getQuoteExpiresAt()==null || !b.getQuoteExpiresAt().toInstant().equals(original.getQuoteExpiresTime().toInstant(ZoneOffset.UTC)))throw incomplete();
            if(!Objects.equals(out.getAfterSaleId(),afterSaleId) || !safeId(out.getOrderItemId()) || out.getRefundNo()==null
                    || !out.getRefundNo().matches("RF[0-9a-f]{32}") || out.getRefundedCount()==null || out.getRefundedCount()<1
                    || out.getRefundedCount()>original.getQuantity() || out.getRestoreCount()==null || out.getRestoreCount()<0
                    || out.getRestoreCount()>out.getRefundedCount() || out.getRefundAmount()==null || out.getRefundAmount()<0
                    || out.getRefundAmount()>original.getPayAmount() || out.getRefundTime()==null || out.getShipped()==null
                    || !out.getShipped() && !Objects.equals(out.getRestoreCount(),out.getRefundedCount())
                    || out.getAfterSaleVersion()==null || out.getAfterSaleVersion()<0 || out.getAfterSaleVersion()>9007199254740991L
                    || out.getOrderVersion()==null || out.getOrderVersion()<0 || out.getOrderVersion()>9007199254740991L
                    || out.getRefundAmount()>0 && !safeId(out.getPayRefundId()) || out.getRefundAmount()==0 && out.getPayRefundId()!=null)throw incomplete();
            return out;
        } finally { calls.release(); }
    }
    private boolean safeId(Long value){return value!=null && value>0 && value<=9007199254740991L;}

    /** 复用原close端点，仅后台持久拒绝20的原引用可调用；已建单回包交调用方保留待核查。 */
    public SeckillQuoteBindingRespDTO closeRejected(SeckillAdmissionIntentDO original) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("拒绝关闭只能在事务外可信后台执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || original==null
                || !Objects.equals(original.getTenantId(),tenant) || !Integer.valueOf(20).equals(original.getStatus())) throw exception0(403,"需要本租户原持久拒绝裁决");
        SeckillRequestDO r=SeckillAdmissionIntentService.frozen(original);
        SeckillQuoteBindingReqDTO request=new SeckillQuoteBindingReqDTO();request.setMemberId(r.getMemberId());request.setRequestNo(r.getRequestNo());
        request.setClientRequestId(r.getClientRequestId());request.setRequestDigest(r.getRequestDigest());request.setQuoteId(r.getQuoteId());request.setQuoteDigest(r.getQuoteDigest());
        if (!token.matches("[A-Za-z0-9_-]{43,128}")) throw exception0(503,"拒绝关闭服务凭据未配置");
        if (!calls.tryAcquire()) throw exception0(503,"原拒绝关闭繁忙");
        try {
            CommonResult<SeckillQuoteBindingRespDTO> reply;
            try { reply=api.close("Bearer "+token,tenant,request); }
            catch (RuntimeException pending) { throw exception0(503,"原拒绝关闭结果尚未核对"); }
            if (reply==null || reply.getCode()==null) throw incomplete();reply.checkError();SeckillQuoteBindingRespDTO out=reply.getData();
            if (out==null || !Objects.equals(out.getTenantId(),tenant) || !Objects.equals(out.getMemberId(),r.getMemberId())
                    || !Objects.equals(out.getRequestNo(),r.getRequestNo()) || !Objects.equals(out.getClientRequestId(),r.getClientRequestId())
                    || !Objects.equals(out.getRequestDigest(),r.getRequestDigest()) || !Objects.equals(out.getQuoteId(),r.getQuoteId())
                    || !Objects.equals(out.getQuoteDigest(),r.getQuoteDigest()) || !Arrays.asList(10,20).contains(out.getState())
                    || out.getFenceVersion()==null || out.getFenceVersion()<1 || out.getFenceVersion()>9007199254740991L || out.getCreatedAt()==null
                    || Integer.valueOf(10).equals(out.getState()) && (out.getOrderId()==null || out.getOrderId()<=0 || out.getOrderId()>9007199254740991L || out.getClosedAt()!=null)
                    || Integer.valueOf(20).equals(out.getState()) && (out.getOrderId()!=null || out.getClosedAt()==null)) throw incomplete();
            return out;
        } finally { calls.release(); }
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原建单结果证明不完整，请核查原申请"); }
}
