package cn.qinghe.mall.module.promotion.framework.trade;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.promotion.framework.member.CouponMemberAccess;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 会员身份构造原引用；报价UUID本身为稳定申请号，失败/重试不另造号。无本地事务内RPC。 */
@Service
public class SeckillQuoteBindingClient {
    private final SeckillQuoteBindingApi api;
    private final CouponMemberAccess access;
    private final String serviceToken;
    private final Semaphore calls=new Semaphore(8);
    public SeckillQuoteBindingClient(SeckillQuoteBindingApi api,CouponMemberAccess access,
            @Value("${qinghe.promotion.seckill-binding.service-token:}") String serviceToken,
            @Value("${qinghe.promotion.trade-base-url:http://127.0.0.1:8083}") String origin) {
        URI uri=URI.create(origin);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())) || !("https".equals(uri.getScheme())
                || "http".equals(uri.getScheme()) && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())))
            throw new IllegalArgumentException("报价绑定来源必须为固定可信origin");
        if (serviceToken==null || !serviceToken.isEmpty() && !serviceToken.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("报价绑定服务凭据格式不正确");
        this.api=api;this.access=access;this.serviceToken=serviceToken;
    }
    public SeckillQuoteBindingRespDTO bind(String clientRequestId,String quoteId,String quoteDigest) {
        SeckillQuoteBindingReqDTO r=original(clientRequestId,quoteId,quoteDigest);Long tenant=access.actor().getTenantId();
        return call(()->api.bind(bearer(),tenant,r),tenant,r,false);
    }
    public SeckillQuoteBindingRespDTO result(String clientRequestId,String quoteId,String quoteDigest) {
        SeckillQuoteBindingReqDTO r=original(clientRequestId,quoteId,quoteDigest);Long tenant=access.actor().getTenantId();
        return call(()->api.result(bearer(),tenant,r),tenant,r,true);
    }
    public SeckillQuoteBindingRespDTO close(String clientRequestId,String quoteId,String quoteDigest) {
        SeckillQuoteBindingReqDTO r=original(clientRequestId,quoteId,quoteDigest);Long tenant=access.actor().getTenantId();
        return call(()->api.close(bearer(),tenant,r),tenant,r,false);
    }
    private SeckillQuoteBindingReqDTO original(String client,String quote,String digest) {
        outside();LoginUser member=access.actor();
        if (!id(member.getTenantId()) || !uuid(client) || !uuid(quote) || digest==null || !digest.matches("[0-9a-f]{64}"))
            throw exception0(400,"秒杀原申请或报价引用不正确");
        SeckillQuoteBindingReqDTO r=new SeckillQuoteBindingReqDTO();r.setMemberId(member.getId());r.setRequestNo(quote);
        r.setClientRequestId(client);r.setQuoteId(quote);r.setQuoteDigest(digest);
        r.setRequestDigest(DigestUtil.sha256Hex("seckill-accept-v1:"+member.getTenantId()+":"+member.getId()+":"+client+":"+quote+":"+digest));return r;
    }
    private SeckillQuoteBindingRespDTO call(Supplier<CommonResult<SeckillQuoteBindingRespDTO>> action,Long tenant,SeckillQuoteBindingReqDTO r,boolean nullable) {
        outside();if (!calls.tryAcquire()) throw exception0(503,"报价绑定繁忙，请查询原申请");
        try {
            CommonResult<SeckillQuoteBindingRespDTO> response;
            try { response=action.get(); }
            catch (cn.qinghe.mall.framework.common.exception.ServiceException known) { throw known; }
            catch (RuntimeException unknown) { throw exception0(503,"报价绑定结果待确认，请查询原申请"); }
            if (response==null || response.getCode()==null) throw incomplete();response.checkError();
            SeckillQuoteBindingRespDTO out=response.getData();if (out==null && nullable) return null;
            if (out==null || !Objects.equals(out.getTenantId(),tenant) || !Objects.equals(out.getMemberId(),r.getMemberId())
                    || !Objects.equals(out.getRequestNo(),r.getRequestNo()) || !Objects.equals(out.getClientRequestId(),r.getClientRequestId())
                    || !Objects.equals(out.getRequestDigest(),r.getRequestDigest()) || !Objects.equals(out.getQuoteId(),r.getQuoteId())
                    || !Objects.equals(out.getQuoteDigest(),r.getQuoteDigest()) || !Arrays.asList(0,10,20).contains(out.getState())
                    || out.getFenceVersion()==null || out.getFenceVersion()<0 || out.getFenceVersion()>9007199254740991L || out.getCreatedAt()==null
                    || Integer.valueOf(10).equals(out.getState()) && !id(out.getOrderId())
                    || !Integer.valueOf(10).equals(out.getState()) && out.getOrderId()!=null
                    || Integer.valueOf(20).equals(out.getState()) && (out.getClosedAt()==null || out.getFenceVersion()<1)
                    || !Integer.valueOf(20).equals(out.getState()) && out.getClosedAt()!=null) throw incomplete();
            if (!Integer.valueOf(20).equals(out.getState()) || out.getActivityId()!=null) {
                if (!id(out.getActivityId()) || out.getGeneration()==null || out.getGeneration()<1 || out.getActivityVersion()==null
                        || out.getActivityVersion()<0 || out.getActivityVersion()>9007199254740991L || !Long.valueOf(1).equals(out.getRedisEpoch())
                        || !id(out.getSkuId()) || out.getQuantity()==null || out.getQuantity()<1 || out.getQuantity()>9999
                        || out.getPriceCent()==null || out.getPriceCent()<1 || out.getPriceCent()>2000000000
                        || !money(out.getGoodsAmount()) || !money(out.getShippingAmount()) || !money(out.getPayAmount())
                        || out.getGoodsAmount()!=((long)out.getPriceCent())*out.getQuantity()
                        || out.getPayAmount()!=out.getGoodsAmount()+out.getShippingAmount() || out.getQuoteExpiresAt()==null) throw incomplete();
            } else if (out.getGeneration()!=null || out.getActivityVersion()!=null || out.getRedisEpoch()!=null || out.getSkuId()!=null
                    || out.getQuantity()!=null || out.getPriceCent()!=null || out.getGoodsAmount()!=null || out.getShippingAmount()!=null
                    || out.getPayAmount()!=null || out.getQuoteExpiresAt()!=null) throw incomplete();
            LoginUser current=access.actor();if (!Objects.equals(current.getTenantId(),tenant) || !Objects.equals(current.getId(),r.getMemberId()))
                throw exception0(401,"报价绑定身份已变化");return out;
        } finally { calls.release(); }
    }
    private String bearer() { if (!serviceToken.matches("[A-Za-z0-9_-]{43,128}")) throw exception0(503,"报价绑定服务凭据未配置");return "Bearer "+serviceToken; }
    private void outside() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("报价绑定RPC必须在本地事务外"); }
    private boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
    private boolean money(Long x) { return x!=null && x>=0 && x<=9007199254740991L; }
    private boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原申请报价绑定回执不完整，请查询核对"); }
}
