package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原cancelOrderByMember/cancelOrderBySystem/cancelOrder0。
 * 本项目先确认支付入口永久关闭，再本地取消；取消后的资源清理进度单独保存。
 * 当前仅内部流程方法，无HTTP/定时执行器；true表示取消及资源完成回执均已确认，清理失败明确保留取消状态。
 * @author 芋道源码
 */
@Service
public class TradeOrderCancelService {
    private final TradeMemberAccess access;
    private final TradeOrderMapper orders;
    private final OrderResourceClient resources;
    private final TradeOrderPaymentSyncService paid;
    private final TradeOrderCancelResourceService cleanup;
    private final TransactionTemplate write;

    public TradeOrderCancelService(TradeMemberAccess access,TradeOrderMapper orders,OrderResourceClient resources,
            TradeOrderPaymentSyncService paid,TradeOrderCancelResourceService cleanup,PlatformTransactionManager manager) {
        this.access=access;this.orders=orders;this.resources=resources;this.paid=paid;this.cleanup=cleanup;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public boolean cancelByMember(HttpServletRequest request,String store,Long orderId) {
        context(orderId);access.check(request,store);LoginUser actor=access.actor();
        TradeOrderDO row=orders.owned(actor.getTenantId(),actor.getId(),orderId);
        if(row==null)throw exception0(404,"本人订单不存在");
        // 历史订单取消不要求canPurchase=true；仍要求当前真实会员与实际商城入口。
        boolean cancelled=cancel(row,30,request,store);
        if(cancelled)completeResources(row);
        return cancelled;
    }

    /** 租户任务调用，租户来自可信任务上下文；不允许浏览器提供userId或取消理由。 */
    public boolean cancelExpired(Long orderId) {
        Long tenant=context(orderId);
        TradeOrderDO row=orders.paymentOrder(tenant,orderId);
        if(row==null)throw exception0(404,"本租户订单不存在");
        if(now().isBefore(row.getPayDeadline()))throw exception0(409,"原付款期限尚未到达");
        boolean cancelled=cancel(row,10,null,null);
        if(cancelled)completeResources(row);
        return cancelled;
    }

    private void completeResources(TradeOrderDO original) {
        if(Integer.valueOf(1).equals(original.getType())) {
            // Seckill resources are released by the existing promotion reconciliation and trade recovery jobs.
            // An HTTP request must not invoke background-only services or ordinary stock/coupon cleanup.
            TradeOrderDO current=orders.paymentOrder(original.getTenantId(),original.getId());
            if(current==null || !Integer.valueOf(1).equals(current.getType())
                    || !Integer.valueOf(40).equals(current.getStatus()) || !Boolean.FALSE.equals(current.getPayStatus()))
                throw exception0(409,"原秒杀取消事实需要核查");
            if(!Boolean.TRUE.equals(current.getCancelResourcesReleased()))
                throw exception0(503,"秒杀订单已取消，活动资源仍待恢复确认，请查询原订单");
            return;
        }
        cleanup.release(original.getId());
    }

    private boolean cancel(TradeOrderDO original,int type,HttpServletRequest request,String store) {
        if(Boolean.TRUE.equals(original.getPayStatus()))return false;
        if(Integer.valueOf(40).equals(original.getStatus()))return true;
        if(!Integer.valueOf(0).equals(original.getStatus()))throw exception0(409,"原订单不能按未付取消");
        PayOrderRespDTO payment=null;
        if(original.getPayPrice()>0) {
            // RPC在所有本地事务/行锁之外；查无账也会按原业务号先落永久关闭记录。
            payment=resources.closePayment(original.getId());
            if(PayOrderStatusEnum.isSuccess(payment.getStatus())) {
                paid.synchronizePaid(original.getId());return false;
            }
            if(!PayOrderStatusEnum.isClosed(payment.getStatus()) || !Boolean.TRUE.equals(payment.getClosing())
                    || Boolean.TRUE.equals(payment.getReviewRequired()) || payment.getSuccessTime()!=null || payment.getRefundPrice()!=0)
                throw exception0(503,"原付款尚未确认未付关闭，不能取消释放");
        }
        Long payId=payment==null?null:payment.getId();
        long checkedAt=System.nanoTime();
        if(type==30) {
            access.check(request,store);
            member(original,checkedAt);
        }
        Boolean cancelled=write.execute(status -> {
            if(type==30)member(original,checkedAt);
            TradeOrderDO row=orders.lockOwned(original.getTenantId(),original.getUserId(),original.getId());
            if(row==null || !Objects.equals(row.getPayPrice(),original.getPayPrice())
                    || !Objects.equals(row.getPayDeadline(),original.getPayDeadline())
                    || row.getPayOrderId()!=null && !Objects.equals(row.getPayOrderId(),payId))
                throw exception0(409,"原订单付款归属或金额已变化");
            if(Boolean.TRUE.equals(row.getPayStatus()))return false;
            if(Integer.valueOf(40).equals(row.getStatus()))return true;
            LocalDateTime instant=now();
            if(orders.cancelUnpaid(row.getTenantId(),row.getUserId(),row.getId(),payId,type,row.getAuthVersion(),instant)!=1)
                throw exception0(409,"原订单取消阶段或版本已变化");
            if(orders.logCancelled(row.getTenantId(),row.getId(),type==30?row.getUserId():0L,
                    type==30?"MEMBER":"SYSTEM",type==30?"MEMBER_CANCEL":"TIMEOUT_CANCEL",
                    Math.addExact(row.getAuthVersion(),1),instant)!=1)throw new IllegalStateException("取消日志保存失败");
            if(type==30)member(original,checkedAt);
            return true;
        });
        if(cancelled==null)throw new IllegalStateException("订单取消事务无结果");return cancelled;
    }

    private void member(TradeOrderDO row,long checkedAt) {
        LoginUser actor=access.actor();
        if(!Objects.equals(actor.getTenantId(),row.getTenantId()) || !Objects.equals(actor.getId(),row.getUserId()))
            throw exception0(403,"当前会员身份已变化");
        if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"取消资格核对已过时，请查询原订单");
    }
    private Long context(Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("取消编排不能放在外层事务内");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(403,"取消需要可信租户与订单编号");
        return tenant;
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
