package cn.qinghe.mall.module.trade.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import cn.qinghe.mall.module.trade.service.order.TradeOrderPaymentSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 择取原cancelOrderBySystem顺序：支付永久关闭后才本地取消，不直接回补秒杀资源。 @author 芋道源码 */
@Service
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
public class TradeSeckillOrderCloseService {
    private final TradeOrderMapper orders;
    private final OrderResourceClient resources;
    private final TradeOrderPaymentSyncService paid;
    private final TransactionTemplate write;
    public TradeSeckillOrderCloseService(TradeOrderMapper orders,OrderResourceClient resources,TradeOrderPaymentSyncService paid,PlatformTransactionManager manager) {
        this.orders=orders;this.resources=resources;this.paid=paid;write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    /** 尚未接调度/HTTP。true只确认本地未付取消，资源释放必须继续核对promotion终局与缓存回执。 */
    public boolean closeExpired(Long orderId) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("秒杀关单仅允许事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || orderId==null || orderId<=0 || orderId>9007199254740991L)throw new IllegalArgumentException("需要可信租户及原订单号");
        TradeOrderDO original=orders.paymentOrder(tenant,orderId);
        if(original==null || !Integer.valueOf(1).equals(original.getType()))throw new IllegalStateException("需要原秒杀正式订单");
        if(Boolean.TRUE.equals(original.getPayStatus()))return false;
        if(now().isBefore(original.getPayDeadline()))throw new IllegalStateException("原付款期限尚未到达");
        PayOrderRespDTO payment=resources.closeSeckillPayment(orderId); // 原支付RPC在所有本地锁/事务外。
        if(PayOrderStatusEnum.isSuccessOrRefund(payment.getStatus())) {
            if(!paid.confirmSeckillLedgerPaid(orderId))throw new IllegalStateException("原付款确认尚未完成");
            return false;
        }
        if(!PayOrderStatusEnum.isClosed(payment.getStatus()) || !Boolean.TRUE.equals(payment.getClosing())
                || Boolean.TRUE.equals(payment.getReviewRequired()) || payment.getSuccessTime()!=null || payment.getRefundPrice()!=0)
            throw new IllegalStateException("原支付尚未确认永久未付关闭，保留原订单和资源");
        Boolean cancelled=write.execute(tx -> {
            TradeOrderDO row=orders.lockOwned(tenant,original.getUserId(),orderId);
            if(row==null || !Integer.valueOf(1).equals(row.getType()) || !Objects.equals(row.getPayPrice(),original.getPayPrice())
                    || !Objects.equals(row.getPayDeadline(),original.getPayDeadline())
                    || row.getPayOrderId()!=null && !Objects.equals(row.getPayOrderId(),payment.getId()))throw new IllegalStateException("原关单付款归属发生变化");
            if(Boolean.TRUE.equals(row.getPayStatus()))return false;
            if(Integer.valueOf(40).equals(row.getStatus())) {
                if(row.getCancelTime()==null || !Integer.valueOf(10).equals(row.getCancelType())
                        || !Objects.equals(row.getPayOrderId(),payment.getId()))throw new IllegalStateException("原关闭事实需要核查");
                return true;
            }
            LocalDateTime instant=now();
            if(orders.cancelUnpaid(tenant,row.getUserId(),orderId,payment.getId(),10,row.getAuthVersion(),instant)!=1
                    || orders.logCancelled(tenant,orderId,0L,"SYSTEM","TIMEOUT_CANCEL",Math.addExact(row.getAuthVersion(),1),instant)!=1)
                throw new IllegalStateException("原秒杀未付取消及日志未同时保存");
            return true;
        });
        if(cancelled==null)throw new IllegalStateException("原秒杀关单未返回结果");return cancelled;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS); }
}
