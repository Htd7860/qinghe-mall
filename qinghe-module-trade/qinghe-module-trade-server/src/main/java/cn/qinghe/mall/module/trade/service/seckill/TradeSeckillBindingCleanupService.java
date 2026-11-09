package cn.qinghe.mall.module.trade.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.LocalDateTime;
import java.util.*;

/** 原报价到期+30分钟覆盖所有合法受理的付款期限；只关建单前栅栏，不推断promotion未受理或释放库存。
 * 复用原TradeOrderAutoCancelJob委托和本项目绑定关闭协议。 @author 芋道源码
 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
public class TradeSeckillBindingCleanupService {
    private final TradeSeckillBindingCleanupMapper cleanup;
    private final TradeSeckillOrderBindingMapper bindings;
    private final TradeSeckillQuoteMapper quotes;
    private final CartMapper members;
    private final TransactionTemplate write;
    public TradeSeckillBindingCleanupService(TradeSeckillBindingCleanupMapper cleanup,TradeSeckillOrderBindingMapper bindings,
            TradeSeckillQuoteMapper quotes,CartMapper members,PlatformTransactionManager manager) {
        this.cleanup=cleanup;this.bindings=bindings;this.quotes=quotes;this.members=members;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public Result recover(int batch,String trace) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("绑定清理只能在事务外可信后台执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || batch<1 || batch>20
                || trace==null || !trace.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("绑定清理上下文不正确");
        List<TradeSeckillOrderBindingDO> rows=cleanup.due(tenant,batch);
        if (rows==null || rows.size()>batch) throw new IllegalStateException("原绑定清理批次不完整");
        Result out=new Result();long started=System.nanoTime();
        for (TradeSeckillOrderBindingDO route:rows) {
            if (route==null || !Objects.equals(route.getTenantId(),tenant)) throw new IllegalStateException("原绑定批次租户不一致");
            if (System.nanoTime()-started>=45000000000L) break;out.attempted++;
            try {
                Boolean done=write.execute(tx -> closeLocked(tenant,route));
                if (done==null) throw new IllegalStateException("原绑定清理未返回裁决");
                if (Boolean.TRUE.equals(done)) out.closed++;else out.skipped++;
            } catch (RuntimeException unknown) {
                out.failed++;
                // 关闭已提交但ACK未知时state已是20，此条件更新不会把它改回待清理。
                try { write.executeWithoutResult(tx -> cleanup.defer(route)); }
                catch (RuntimeException unavailable) { log.warn("Seckill binding cleanup metadata pending tenant={},request={},trace={}",tenant,route.getRequestNo(),trace); }
                log.warn("Seckill binding cleanup pending tenant={},request={},trace={}",tenant,route.getRequestNo(),trace);
            }
        }
        out.manual=cleanup.manual(tenant);
        if (out.manual>0) log.error("Seckill bindings require original-fence reconciliation tenant={},manual={},trace={}",tenant,out.manual,trace);
        return out;
    }
    private boolean closeLocked(Long tenant,TradeSeckillOrderBindingDO route) {
        // 与bind/CREATE/CLOSE完全相同的锁序；已建单10与原关闭20都不再操作。
        members.ensureUser(tenant,route.getMemberId());
        if (members.lockVersion(tenant,route.getMemberId())==null) throw new IllegalStateException("原会员父记录缺失");
        TradeSeckillOrderBindingDO current=bindings.lock(tenant,route.getRequestNo());
        if (current==null || !Objects.equals(current.getMemberId(),route.getMemberId()) || !Objects.equals(current.getQuoteId(),route.getQuoteId())
                || !Objects.equals(current.getQuoteDigest(),route.getQuoteDigest())) throw new IllegalStateException("原绑定引用已变化");
        if (Integer.valueOf(10).equals(current.getState()) || Integer.valueOf(20).equals(current.getState())) return false;
        LocalDateTime now=quotes.now();
        if (!Integer.valueOf(0).equals(current.getState()) || current.getOrderId()!=null || current.getQuoteExpiresAt()==null
                || current.getNextCleanupAt()==null || current.getCleanupAttempts()==null) throw new IllegalStateException("原绑定清理事实不完整");
        if (current.getCleanupAttempts()>=10 || now.isBefore(current.getNextCleanupAt()) || now.isBefore(current.getQuoteExpiresAt().plusMinutes(30))) return false;
        TradeSeckillQuoteDO quote=quotes.lock(tenant,current.getMemberId(),current.getQuoteId());
        sameQuote(current,quote);
        if (cleanup.closeExpired(current)!=1 || quotes.closeSnapshot(tenant,current.getMemberId(),current.getQuoteId(),current.getQuoteDigest(),current.getRequestNo())!=1)
            throw new IllegalStateException("原过期绑定及报价密文未同时关闭");
        return true;
    }
    private void sameQuote(TradeSeckillOrderBindingDO b,TradeSeckillQuoteDO q) {
        if (q==null || !Objects.equals(q.getTenantId(),b.getTenantId()) || !Objects.equals(q.getMemberId(),b.getMemberId())
                || !Objects.equals(q.getQuoteId(),b.getQuoteId()) || !Objects.equals(q.getQuoteDigest(),b.getQuoteDigest())
                || !Objects.equals(q.getBoundRequestNo(),b.getRequestNo()) || q.getBoundOrderId()!=null || q.getSnapshotCipher()==null
                || !Objects.equals(q.getActivityId(),b.getActivityId()) || !Objects.equals(q.getGeneration(),b.getGeneration())
                || !Objects.equals(q.getActivityVersion(),b.getActivityVersion()) || !Objects.equals(q.getRedisEpoch(),b.getRedisEpoch())
                || !Objects.equals(q.getSkuId(),b.getSkuId()) || !Objects.equals(q.getQuantity(),b.getQuantity())
                || !Objects.equals(q.getPriceCent(),b.getPriceCent()) || !Objects.equals(q.getGoodsAmount(),b.getGoodsAmount())
                || !Objects.equals(q.getShippingAmount(),b.getShippingAmount()) || !Objects.equals(q.getPayAmount(),b.getPayAmount())
                || !Objects.equals(q.getExpiresAt(),b.getQuoteExpiresAt())) throw new IllegalStateException("原绑定与报价事实不一致");
    }
    @Data public static class Result { private int attempted;private int closed;private int skipped;private int failed;private long manual; }
}
