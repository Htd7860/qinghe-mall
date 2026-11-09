package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原TradeProductSkuOrderHandler/TradeCouponOrderHandler.afterCancelOrder。
 * 复用现有原订单幂等账本；任一侧未知保留取消待清理，只按原单恢复，无通用重试框架。
 * @author 芋道源码
 */
@Service
@Slf4j
public class TradeOrderCancelResourceService {
    private final TradeOrderMapper orders;
    private final OrderResourceClient resources;
    private final TransactionTemplate write;

    public TradeOrderCancelResourceService(TradeOrderMapper orders,OrderResourceClient resources,PlatformTransactionManager manager) {
        this.orders=orders;this.resources=resources;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** 仅可信取消流程/恢复任务调用，无HTTP；返回时两侧回执及本地完成标记均已确认。 */
    public void release(Long orderId) {
        Long tenant=context(orderId);
        TradeOrderDO original=orders.paymentOrder(tenant,orderId);
        cancelled(original);
        if(Boolean.TRUE.equals(original.getCancelResourcesReleased()))return;
        requireClosedPayment(original);

        boolean confirmed=true;
        try {resources.closeCancelledStock(orderId);} catch(RuntimeException error) {
            confirmed=false;log.warn("取消库存回补待确认 tenant={}, order={}",tenant,orderId);
        }
        try {resources.closeCancelledCoupon(orderId);} catch(RuntimeException error) {
            confirmed=false;log.warn("取消优惠券退回待确认 tenant={}, order={}",tenant,orderId);
        }
        if(!confirmed)throw exception0(503,"订单已取消，资源清理待确认，请按原订单恢复");

        // 观察到异常晚付时保留待核查，不把已经清理的资源重新占回，不伪造完成。
        requireClosedPayment(original);
        write.execute(status -> {
            TradeOrderDO row=orders.lockOwned(tenant,original.getUserId(),orderId);
            cancelled(row);
            if(!Objects.equals(row.getPayOrderId(),original.getPayOrderId()) || !Objects.equals(row.getPayPrice(),original.getPayPrice())
                    || !Objects.equals(row.getCouponId(),original.getCouponId()) || !Objects.equals(row.getCancelTime(),original.getCancelTime())
                    || !Objects.equals(row.getCancelType(),original.getCancelType()))throw exception0(409,"原取消订单归属或阶段已变化");
            if(Boolean.TRUE.equals(row.getCancelResourcesReleased()))return null;
            LocalDateTime instant=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
            if(orders.completeCancelResources(tenant,row.getUserId(),orderId,row.getAuthVersion(),instant)!=1)
                throw exception0(409,"取消资源确认版本已变化");
            if(orders.logCancelReleased(tenant,orderId,Math.addExact(row.getAuthVersion(),1),instant)!=1)
                throw new IllegalStateException("取消资源完成日志保存失败");
            return null;
        });
    }

    private void requireClosedPayment(TradeOrderDO row) {
        if(row.getPayPrice()==0) {
            if(row.getPayOrderId()!=null)throw exception0(409,"零元取消订单存在异常支付归属");
            return;
        }
        PayOrderRespDTO payment=resources.closePayment(row.getId());
        if(!Objects.equals(row.getPayOrderId(),payment.getId()) || !PayOrderStatusEnum.isClosed(payment.getStatus())
                || !Boolean.TRUE.equals(payment.getClosing()) || Boolean.TRUE.equals(payment.getReviewRequired())
                || payment.getSuccessTime()!=null || payment.getRefundPrice()!=0)
            throw exception0(409,"原付款未确认未付关闭，取消资源需要核查");
    }
    private void cancelled(TradeOrderDO row) {
        if(row==null)throw exception0(404,"本租户订单不存在");
        if(!Integer.valueOf(40).equals(row.getStatus()) || !Boolean.FALSE.equals(row.getPayStatus())
                || row.getCancelTime()==null || !Arrays.asList(10,30).contains(row.getCancelType()) || row.getCancelResourcesReleased()==null)
            throw exception0(409,"原订单不是待清理的未付取消订单");
    }
    private Long context(Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("取消资源RPC不能放在外层事务内");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(403,"取消清理需要可信租户与原订单编号");
        return tenant;
    }
}
