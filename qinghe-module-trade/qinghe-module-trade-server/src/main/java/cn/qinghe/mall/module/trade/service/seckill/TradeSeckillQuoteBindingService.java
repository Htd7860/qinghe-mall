package cn.qinghe.mall.module.trade.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 沿用原订单准备的本地报价绑定/幂等回执职责。只绑定报价或永久关闭建单栅栏，不创建订单/扣库存/发MQ。
 * 当前trade专用服务身份只在精确内部POST上授予；不接受普通会员/员工身份。
 */
@Service
@PreAuthorize("hasAuthority('internal:trade:order-command')")
public class TradeSeckillQuoteBindingService {
    private final TradeSeckillQuoteMapper quotes;
    private final TradeSeckillOrderBindingMapper bindings;
    private final CartMapper members;
    private final TradeSeckillSnapshotReader snapshots;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    public TradeSeckillQuoteBindingService(TradeSeckillQuoteMapper quotes,TradeSeckillOrderBindingMapper bindings,
            CartMapper members,TradeSeckillSnapshotReader snapshots,PlatformTransactionManager manager) {
        this.quotes=quotes;this.bindings=bindings;this.members=members;this.snapshots=snapshots;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }

    public SeckillQuoteBindingRespDTO bind(SeckillQuoteBindingReqDTO request) {
        Long tenant=validate(request);
        return write.execute(tx -> {
            memberLock(tenant,request.getMemberId());
            TradeSeckillOrderBindingDO row=originalLocked(tenant,request);
            // 包括已过期的原绑定及关闭先到，均只返回原事实，不续期、不重新打开。
            if (row.getState()!=30) return project(row);
            TradeSeckillQuoteDO quote=quotes.lock(tenant,request.getMemberId(),request.getQuoteId());
            sameQuote(quote,request);
            if (quote.getBoundRequestNo()!=null || quote.getBoundOrderId()!=null || quote.getSnapshotCipher()==null)
                throw exception0(409,"原报价已使用或已关闭");
            snapshots.openVerified(quote);
            // 最终过期判断使用数据库当前时间，不能只依赖锁前Java判断。
            if (quotes.bind(tenant,request.getMemberId(),request.getQuoteId(),request.getQuoteDigest(),request.getRequestNo())!=1)
                throw exception0(409,"原报价已过期或不可绑定");
            if (bindings.bindQuote(request.getRequestNo(),quote)!=1) throw new IllegalStateException("原报价申请绑定失败");
            return project(bindings.lock(tenant,request.getRequestNo()));
        });
    }

    /** CLOSE先到亦落永久墓碑。当前无订单才可清密文，未来CREATE必须锁同一栅栏且拒绝state20。 */
    public SeckillQuoteBindingRespDTO close(SeckillQuoteBindingReqDTO request) {
        Long tenant=validate(request);
        return write.execute(tx -> {
            memberLock(tenant,request.getMemberId());
            TradeSeckillOrderBindingDO row=originalLocked(tenant,request);
            if (Integer.valueOf(20).equals(row.getState()) || Integer.valueOf(10).equals(row.getState())) return project(row);
            TradeSeckillQuoteDO quote=quotes.lock(tenant,request.getMemberId(),request.getQuoteId());
            if (quote!=null) {
                sameQuote(quote,request);
                if (quote.getBoundOrderId()!=null || quote.getBoundRequestNo()!=null && !request.getRequestNo().equals(quote.getBoundRequestNo()))
                    throw exception0(409,"原报价已有不同绑定，请核对原结果");
            }
            if (bindings.closeBeforeOrder(tenant,request.getRequestNo())!=1) throw new IllegalStateException("原申请关闭栅栏失败");
            if (quote!=null && quotes.closeSnapshot(tenant,request.getMemberId(),request.getQuoteId(),request.getQuoteDigest(),request.getRequestNo())!=1)
                throw new IllegalStateException("关闭栅栏后清理原报价失败");
            return project(bindings.lock(tenant,request.getRequestNo()));
        });
    }

    /** 纯读。null不触发换申请号、重绑或自动释放promotion/Redis占额。 */
    public SeckillQuoteBindingRespDTO result(SeckillQuoteBindingReqDTO request) {
        Long tenant=validate(request);
        return read.execute(tx -> {
            TradeSeckillOrderBindingDO row=bindings.get(tenant,request.getRequestNo());
            if (row==null) return null;same(row,request);return project(row);
        });
    }

    /** 只清未绑定且过期的报价密文，保留元数据；未接后台扫描或HTTP。 */
    public int clearExpired(Long member) {
        outside();Long tenant=tenant();if (!id(member)) throw exception0(400,"会员编号不正确");
        return write.execute(tx -> { memberLock(tenant,member);return quotes.clearExpired(tenant,member); });
    }
    private TradeSeckillOrderBindingDO originalLocked(Long tenant,SeckillQuoteBindingReqDTO request) {
        bindings.ensure(tenant,request);
        TradeSeckillOrderBindingDO row=bindings.lock(tenant,request.getRequestNo());
        // 其他原申请已占相同clientRequestId或quoteId的唯一键，绝不能重置其记录。
        if (row==null) throw exception0(409,"原请求号或报价已归属另一申请");
        same(row,request);return row;
    }
    private void memberLock(Long tenant,Long member) {
        members.ensureUser(tenant,member);
        if (members.lockVersion(tenant,member)==null) throw new IllegalStateException("会员报价父锁不存在");
    }
    private void sameQuote(TradeSeckillQuoteDO quote,SeckillQuoteBindingReqDTO r) {
        if (quote==null) throw exception0(404,"原会员报价不存在");
        if (!Objects.equals(quote.getMemberId(),r.getMemberId()) || !Objects.equals(quote.getQuoteId(),r.getQuoteId())
                || !Objects.equals(quote.getQuoteDigest(),r.getQuoteDigest())) throw exception0(409,"原报价引用不匹配");
    }
    private void same(TradeSeckillOrderBindingDO row,SeckillQuoteBindingReqDTO r) {
        if (!Objects.equals(row.getMemberId(),r.getMemberId()) || !Objects.equals(row.getRequestNo(),r.getRequestNo())
                || !Objects.equals(row.getClientRequestId(),r.getClientRequestId()) || !Objects.equals(row.getRequestDigest(),r.getRequestDigest())
                || !Objects.equals(row.getQuoteId(),r.getQuoteId()) || !Objects.equals(row.getQuoteDigest(),r.getQuoteDigest()))
            throw exception0(409,"原申请引用不能更换会员、请求参数或报价");
    }
    public static SeckillQuoteBindingRespDTO project(TradeSeckillOrderBindingDO row) {
        if (row==null || !Arrays.asList(0,10,20).contains(row.getState())) throw exception0(503,"原报价绑定结果不完整");
        SeckillQuoteBindingRespDTO out=new SeckillQuoteBindingRespDTO();out.setTenantId(row.getTenantId());out.setMemberId(row.getMemberId());
        out.setRequestNo(row.getRequestNo());out.setClientRequestId(row.getClientRequestId());out.setRequestDigest(row.getRequestDigest());
        out.setQuoteId(row.getQuoteId());out.setQuoteDigest(row.getQuoteDigest());out.setState(row.getState());out.setFenceVersion(row.getFenceVersion());
        out.setOrderId(row.getOrderId());out.setActivityId(row.getActivityId());out.setGeneration(row.getGeneration());out.setActivityVersion(row.getActivityVersion());
        out.setRedisEpoch(row.getRedisEpoch());out.setSkuId(row.getSkuId());out.setQuantity(row.getQuantity());out.setPriceCent(row.getPriceCent());
        out.setGoodsAmount(row.getGoodsAmount());out.setShippingAmount(row.getShippingAmount());out.setPayAmount(row.getPayAmount());
        out.setQuoteExpiresAt(row.getQuoteExpiresAt()==null?null:row.getQuoteExpiresAt().atOffset(ZoneOffset.UTC));
        out.setCreatedAt(row.getCreatedAt().atOffset(ZoneOffset.UTC));out.setClosedAt(row.getClosedAt()==null?null:row.getClosedAt().atOffset(ZoneOffset.UTC));return out;
    }
    private Long validate(SeckillQuoteBindingReqDTO r) {
        outside();Long tenant=tenant();
        if (r==null || !id(r.getMemberId()) || !uuid(r.getRequestNo()) || !uuid(r.getClientRequestId()) || !uuid(r.getQuoteId())
                || !digest(r.getRequestDigest()) || !digest(r.getQuoteDigest())) throw exception0(400,"原秒杀申请引用不正确");
        return tenant;
    }
    private Long tenant() { Long id=TenantContextHolder.getTenantId();if (!id(id) || TenantContextHolder.isIgnore()) throw exception0(403,"需要可信当前租户");return id; }
    private void outside() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("报价绑定不能嵌套外层事务"); }
    private boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
    private boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private boolean digest(String x) { return x!=null && x.matches("[0-9a-f]{64}"); }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原秒杀报价或绑定记录不完整，请按原申请核查"); }
}
