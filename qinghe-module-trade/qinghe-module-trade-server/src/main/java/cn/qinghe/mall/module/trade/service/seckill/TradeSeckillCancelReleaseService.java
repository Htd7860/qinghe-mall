package cn.qinghe.mall.module.trade.service.seckill;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.TradeSeckillOrderBindingMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import cn.qinghe.mall.module.trade.framework.seckill.SeckillDispatchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 原取消资源回执职责：只确认秒杀已实际退占用/缓存完成，不调用普通库存或券。 @author 芋道源码 */
@Service
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
public class TradeSeckillCancelReleaseService {
    private final TradeOrderMapper orders;
    private final TradeOrderPrepareMapper preparations;
    private final TradeSeckillOrderBindingMapper bindings;
    private final OrderResourceClient payments;
    private final SeckillDispatchClient promotion;
    private final TransactionTemplate read,write;
    public TradeSeckillCancelReleaseService(TradeOrderMapper orders,TradeOrderPrepareMapper preparations,TradeSeckillOrderBindingMapper bindings,
            OrderResourceClient payments,SeckillDispatchClient promotion,PlatformTransactionManager manager) {
        this.orders=orders;this.preparations=preparations;this.bindings=bindings;this.payments=payments;this.promotion=promotion;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    /** false为原资源终局/缓存仍待确认，原恢复任务保持READY等待，不报告完成。 */
    public boolean release(Long orderId) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("秒杀资源确认只允许事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || orderId==null || orderId<=0 || orderId>9007199254740991L)throw new IllegalArgumentException("需要原租户订单号");
        TradeOrderDO original=read.execute(tx -> {
            cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO p=preparations.get(tenant,orderId);
            if(p==null || !Integer.valueOf(1).equals(p.getSourceType()))throw new IllegalStateException("原秒杀准备记录缺失");
            return TradeSeckillResultReadService.validatedOrder(orders,preparations,tenant,bindings.get(tenant,p.getRequestId()));
        });
        cancelled(original);if(!Objects.equals(original.getId(),orderId))throw new IllegalStateException("原秒杀订单引用不一致");
        closedPayment(original);
        if(Boolean.TRUE.equals(original.getCancelResourcesReleased()))return true;
        SeckillResourceLookupDTO q=read.execute(tx -> {
            cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO p=preparations.get(tenant,orderId);
            if(p==null)throw new IllegalStateException("原准备记录缺失");
            SeckillResourceLookupDTO query=new SeckillResourceLookupDTO();query.setRequestNo(p.getRequestId());query.setRequestDigest(p.getRequestHash());
            query.setMemberId(original.getUserId());query.setOrderId(orderId);return query;
        });
        SeckillResourceReceiptDTO receipt=promotion.resource(q);
        if(receipt==null)return false;
        if(!"CANCELLED".equals(receipt.getState()) || !Objects.equals(receipt.getPayOrderId(),original.getPayOrderId())
                || !receipt.getResourceTime().toInstant().equals(original.getCancelTime().truncatedTo(ChronoUnit.MILLIS)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant())
                || receipt.getOrderVersion()>original.getAuthVersion())throw new IllegalStateException("原取消与资源终局冲突");
        if(!"SYNCED".equals(receipt.getCacheState()))return false;
        closedPayment(original); // 异常晚付保留待核查，不能伪造资源完成。
        Boolean done=write.execute(tx -> {
            TradeOrderDO r=orders.lockOwned(tenant,original.getUserId(),orderId);cancelled(r);
            if(!Objects.equals(r.getPayOrderId(),original.getPayOrderId()) || !Objects.equals(r.getPayPrice(),original.getPayPrice())
                    || !Objects.equals(r.getCancelTime(),original.getCancelTime()) || !Objects.equals(r.getCancelType(),original.getCancelType()))
                throw new IllegalStateException("原取消事实已变化");
            if(Boolean.TRUE.equals(r.getCancelResourcesReleased()))return true;
            LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
            if(orders.completeCancelResources(tenant,r.getUserId(),orderId,r.getAuthVersion(),now)!=1
                    || orders.logCancelReleased(tenant,orderId,Math.addExact(r.getAuthVersion(),1),now)!=1)throw new IllegalStateException("原取消资源确认与日志未同时保存");
            return true;
        });
        if(done==null)throw new IllegalStateException("原取消资源确认结果未知");return done;
    }
    private void closedPayment(TradeOrderDO o) {
        PayOrderRespDTO p=payments.closeSeckillPayment(o.getId());
        if(!Objects.equals(p.getId(),o.getPayOrderId()) || !PayOrderStatusEnum.isClosed(p.getStatus()) || !Boolean.TRUE.equals(p.getClosing())
                || Boolean.TRUE.equals(p.getReviewRequired()) || p.getSuccessTime()!=null || p.getRefundPrice()!=0)
            throw new IllegalStateException("原付款未永久未付关闭，不能确认回补完成");
    }
    private void cancelled(TradeOrderDO o) {
        if(o==null || !Integer.valueOf(1).equals(o.getType()) || !Integer.valueOf(40).equals(o.getStatus()) || !Boolean.FALSE.equals(o.getPayStatus())
                || o.getCancelTime()==null || !Arrays.asList(10,30).contains(o.getCancelType()) || o.getCancelResourcesReleased()==null)
            throw new IllegalStateException("原订单不是未付取消的秒杀订单");
    }
}
