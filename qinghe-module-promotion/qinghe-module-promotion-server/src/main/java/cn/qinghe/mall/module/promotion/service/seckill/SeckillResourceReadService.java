package cn.qinghe.mall.module.promotion.service.seckill;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原终局证明只读，HTTP内不调用trade或Redis，不触发资源转换。 */
@Service @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class SeckillResourceReadService {
    private final SeckillAdmissionMapper requests;
    private final SeckillOrderResourceMapper resources;
    private final TransactionTemplate read;
    public SeckillResourceReadService(SeckillAdmissionMapper requests,SeckillOrderResourceMapper resources,PlatformTransactionManager manager) {
        this.requests=requests;this.resources=resources;read=new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);read.setTimeout(5);
    }
    public SeckillResourceReceiptDTO get(SeckillResourceLookupDTO q) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("原资源读取不能嵌套事务");
        Long tenant=TenantContextHolder.getTenantId();
        if(!id(tenant) || TenantContextHolder.isIgnore())throw exception0(403,"需要可信当前租户");
        if(q==null || !id(q.getMemberId()) || !id(q.getOrderId()) || q.getRequestNo()==null
                || !q.getRequestNo().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || q.getRequestDigest()==null || !q.getRequestDigest().matches("[0-9a-f]{64}"))throw exception0(400,"需要原订单申请引用");
        return read.execute(tx -> {
            SeckillRequestDO r=requests.forDispatch(tenant,q.getRequestNo());
            if(r==null)return null;
            if(!Objects.equals(r.getMemberId(),q.getMemberId()) || !Objects.equals(r.getRequestDigest(),q.getRequestDigest())
                    || r.getOrderId()!=null && !Objects.equals(r.getOrderId(),q.getOrderId()))throw exception0(409,"原资源请求归属不一致");
            if(r.getOrderId()==null || "HELD".equals(r.getResourceState()))return null;
            SeckillRequestDO proof=resources.cacheProof(tenant,q.getRequestNo());
            if(proof==null)throw exception0(503,"原资源终局与流水不一致");
            SeckillResourceReceiptDTO out=new SeckillResourceReceiptDTO();out.setTenantId(tenant);out.setRequestNo(proof.getRequestNo());
            out.setMemberId(proof.getMemberId());out.setOrderId(proof.getOrderId());out.setRequestDigest(proof.getRequestDigest());
            out.setState(proof.getResourceState());out.setCacheState(proof.getResourceCacheState());out.setOrderVersion(proof.getResourceOrderVersion());
            out.setPayOrderId(proof.getResourcePayOrderId());out.setResourceTime(proof.getResourceTime().atOffset(ZoneOffset.UTC));out.setRequestVersion(proof.getVersion());return out;
        });
    }
    public SeckillPaymentEligibilityDTO paymentEligibility(SeckillResourceLookupDTO q) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("付款资格读取不能嵌套事务");
        Long tenant=TenantContextHolder.getTenantId();
        if(!id(tenant) || TenantContextHolder.isIgnore())throw exception0(403,"需要可信当前租户");
        if(q==null || !id(q.getMemberId()) || !id(q.getOrderId()) || q.getRequestNo()==null
                || !q.getRequestNo().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || q.getRequestDigest()==null || !q.getRequestDigest().matches("[0-9a-f]{64}"))throw exception0(400,"需要原付款申请引用");
        return read.execute(tx -> {
            SeckillRequestDO r=requests.forDispatch(tenant,q.getRequestNo());
            if(r==null || !Objects.equals(r.getMemberId(),q.getMemberId()) || !Objects.equals(r.getOrderId(),q.getOrderId())
                    || !Objects.equals(r.getRequestDigest(),q.getRequestDigest()))throw exception0(409,"原申请尚未绑定该订单");
            SeckillRequestReceiptService.project(r);
            SeckillPaymentEligibilityDTO out=new SeckillPaymentEligibilityDTO();out.setTenantId(tenant);out.setRequestNo(r.getRequestNo());
            out.setMemberId(r.getMemberId());out.setOrderId(r.getOrderId());out.setRequestDigest(r.getRequestDigest());
            out.setRequestVersion(r.getVersion());out.setPayDeadline(r.getPayDeadline().atOffset(ZoneOffset.UTC));
            out.setCanPay(Integer.valueOf(20).equals(r.getStatus()) && "HELD".equals(r.getResourceState())
                    && r.getResourceCheckAttempts()!=null && r.getResourceCheckAttempts()<10 && requests.now().isBefore(r.getPayDeadline()));
            return out;
        });
    }
    private boolean id(Long id) { return id!=null && id>0 && id<=9007199254740991L; }
}
