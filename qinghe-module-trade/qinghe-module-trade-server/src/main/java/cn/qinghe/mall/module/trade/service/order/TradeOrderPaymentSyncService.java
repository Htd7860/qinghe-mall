package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原updateOrderPaid的支付账核对与条件更新；不接受浏览器传入支付成功DTO。
 * 仅供可信订单流程/恢复任务调用，无新增HTTP。false仅表示未确认付款，不是可以关单回补的依据。
 * @author 芋道源码
 */
@Service
public class TradeOrderPaymentSyncService {
    private final TradeOrderMapper orders;
    private final OrderResourceClient resources;
    private final TransactionTemplate write;
    public TradeOrderPaymentSyncService(TradeOrderMapper orders,OrderResourceClient resources,PlatformTransactionManager manager) {
        this.orders=orders;this.resources=resources;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public boolean synchronizePaid(Long orderId) {
        return synchronizePaid(orderId,true);
    }
    /** 显式提交/查渠道已执行后仅核对原支付账，避免再次调用渠道。 */
    public boolean confirmLedgerPaid(Long orderId) {
        return synchronizePaid(orderId,false);
    }
    /** 关闭原秒杀支付返回已付时复用同一付款确认事务；仅查原支付账，不创建/提交支付。 */
    public boolean confirmSeckillLedgerPaid(Long orderId) {
        return synchronizePaid(orderId,false,true);
    }
    public boolean synchronizeSeckillPaid(Long orderId) {
        return synchronizePaid(orderId,true,true);
    }
    private boolean synchronizePaid(Long orderId,boolean queryChannel) {
        return synchronizePaid(orderId,queryChannel,false);
    }
    private boolean synchronizePaid(Long orderId,boolean queryChannel,boolean seckill) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("付款同步必须在外层事务之外");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || orderId==null || orderId<=0 || orderId>9_007_199_254_740_991L)throw exception0(403,"付款同步需要可信租户与订单编号");
        TradeOrderDO original=orders.paymentOrder(tenant,orderId);
        if(original==null)throw exception0(404,"本租户订单不存在");
        if(original.getPayPrice()==0)return Boolean.TRUE.equals(original.getPayStatus());
        // 固定服务凭据查询支付库结果，RPC在本地事务外；client核对原金额/会员/应用/绝对期限。
        PayOrderRespDTO payment=seckill?(queryChannel?resources.synchronizeSeckillPayment(orderId):resources.seckillPaymentResult(orderId))
                :queryChannel?resources.synchronizePayment(orderId):resources.paymentResult(orderId);
        if(payment==null)return false;
        if(Boolean.TRUE.equals(payment.getReviewRequired()))throw exception0(409,"原付款进入核查，不能直接恢复履约");
        if(!PayOrderStatusEnum.isSuccessOrRefund(payment.getStatus()))return false;
        if(payment.getSuccessTime()==null || payment.getSuccessTime().isBefore(original.getCreateTime().minusSeconds(1))
                || !payment.getSuccessTime().isBefore(original.getPayDeadline()))
            throw exception0(409,"原付款时间或退款状态需要核查");

        Boolean confirmed=write.execute(status -> {
            TradeOrderDO row=orders.lockOwned(tenant,original.getUserId(),orderId);
            if(row==null || !Objects.equals(row.getPayPrice(),payment.getPrice()) || !Objects.equals(row.getPayDeadline(),payment.getExpireTime())
                    || row.getPayOrderId()!=null && !Objects.equals(row.getPayOrderId(),payment.getId()))throw exception0(409,"原订单支付归属不一致");
            if(Boolean.TRUE.equals(row.getPayStatus())) {
                if(!Objects.equals(row.getPayOrderId(),payment.getId()) || !Objects.equals(row.getPayTime(),payment.getSuccessTime()))
                    throw exception0(409,"原订单已确认其他付款凭据");
                return true;
            }
            // 原已付订单发生退款，付款事实仍成立；不能据退款账替未确认订单新建履约资格。
            if(!PayOrderStatusEnum.isSuccess(payment.getStatus()) || payment.getRefundPrice()!=0)
                throw exception0(409,"原付款已有退款，未确认订单需要单独核查");
            if(row.getStatus()!=0)throw exception0(409,"原订单已停止履约，付款需要单独核查");
            LocalDateTime instant=now();
            if(orders.confirmPaid(tenant,row.getUserId(),orderId,payment.getId(),payment.getSuccessTime(),row.getAuthVersion(),instant)!=1)
                throw exception0(409,"原订单付款阶段或版本已变化");
            if(orders.logPaid(tenant,orderId,Math.addExact(row.getAuthVersion(),1),instant)!=1)
                throw new IllegalStateException("付款确认日志保存失败");
            return true;
        });
        if(confirmed==null)throw new IllegalStateException("付款同步事务无结果");return confirmed;
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
