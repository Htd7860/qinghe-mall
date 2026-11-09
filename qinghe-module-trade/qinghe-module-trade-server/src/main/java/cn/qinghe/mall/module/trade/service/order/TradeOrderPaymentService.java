package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderSubmissionRespDTO;
import cn.qinghe.mall.module.trade.controller.app.order.vo.AppTradeOrderPageItemRespVO;
import cn.qinghe.mall.module.trade.controller.app.order.vo.AppTradeOrderPaymentRespVO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderPrepareMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderPaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原createPayOrder的创建→绑定职责；远程账已提交但本地未知时，原业务号查询/重试，不释放商品资源。
 * 零元原单在本地确认，无假渠道付款/假支付单。显式提交/查原渠道，不提供浏览器报付款成功HTTP。
 * @author 芋道源码
 */
@Service
public class TradeOrderPaymentService {
    private final TradeMemberAccess access;
    private final TradeOrderMapper orders;
    private final TradeOrderPrepareMapper preparations;
    private final OrderResourceClient resources;
    private final TradeOrderQueryService queries;
    private final TradeOrderPaymentSyncService synchronization;
    private final boolean seckillEnabled;
    private final TransactionTemplate write;

    public TradeOrderPaymentService(TradeMemberAccess access,TradeOrderMapper orders,TradeOrderPrepareMapper preparations,
            OrderResourceClient resources,PlatformTransactionManager manager,TradeOrderQueryService queries,TradeOrderPaymentSyncService synchronization,
            @Value("${qinghe.trade.seckill-orders.enabled:false}") boolean seckillEnabled) {
        this.access=access;this.orders=orders;this.preparations=preparations;this.resources=resources;
        this.queries=queries;this.synchronization=synchronization;
        this.seckillEnabled=seckillEnabled;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** 只读原账/原二维码，刷新页面不会偷偷发起渠道付款。 */
    public AppTradeOrderPaymentRespVO paymentResult(HttpServletRequest request,String store,Long id) {
        access.check(request,store);TradeOrderDO original=owned(id);
        PayOrderSubmissionRespDTO payment=original.getPayOrderId()==null?null:resources.paymentSubmission(id,false);
        return paymentView(request,store,id,payment);
    }
    /** 浏览器仅传原订单id；准备原账后，显式提交固定扫码渠道。 */
    public AppTradeOrderPaymentRespVO submit(HttpServletRequest request,String store,Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("付款提交必须在外层事务之外");
        // prepare已统一核对入口/本人/期限并绑定原账，避免重写一套前置校验。
        prepare(request,store,id);
        TradeOrderDO current=owned(id);
        if(Boolean.TRUE.equals(current.getPayStatus()))return paymentView(request,store,id,null);
        unpaid(current);
        if(!Boolean.TRUE.equals(access.check(request,store).getCanPurchase()))throw exception0(403,"店铺暂不支持新购买");
        PayOrderSubmissionRespDTO payment=resources.paymentSubmission(id,true);
        confirmPayment(id,payment);
        return paymentView(request,store,id,payment);
    }
    /** 主动查渠道可能入账，故用POST；无支付账不创建、不换流水。 */
    public AppTradeOrderPaymentRespVO queryPayment(HttpServletRequest request,String store,Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("付款查单必须在外层事务之外");
        access.check(request,store);TradeOrderDO original=owned(id);
        if(original.getPayPrice()==0)return paymentView(request,store,id,null);
        resources.synchronizePayment(id);
        PayOrderSubmissionRespDTO payment=resources.paymentSubmission(id,false);
        confirmPayment(id,payment);
        return paymentView(request,store,id,payment);
    }
    /** 显式恢复已存在的原尝试；不prepare、不创建新账，调用方不能指定流水或续期。 */
    public AppTradeOrderPaymentRespVO recoverQr(HttpServletRequest request,String store,Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("二维码恢复必须在外层事务之外");
        MemberStorefrontRespDTO entry=access.check(request,store);TradeOrderDO original=owned(id);
        if(Boolean.TRUE.equals(original.getPayStatus()))return paymentView(request,store,id,null);
        unpaid(original);
        if(original.getPayPrice()<=0 || original.getPayOrderId()==null)throw exception0(409,"原扫码付款尚未建立，请先查询原订单");
        if(!Boolean.TRUE.equals(entry.getCanPurchase()))throw exception0(403,"店铺暂不支持继续付款");
        PayOrderSubmissionRespDTO payment=resources.recoverPaymentQr(id);
        confirmPayment(id,payment);
        return paymentView(request,store,id,payment);
    }
    private void confirmPayment(Long id,PayOrderSubmissionRespDTO payment) {
        if(payment!=null && !Boolean.TRUE.equals(payment.getPayment().getReviewRequired())
                && PayOrderStatusEnum.isSuccessOrRefund(payment.getPayment().getStatus()))synchronization.confirmLedgerPaid(id);
    }
    private TradeOrderDO owned(Long id) {
        if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(400,"订单编号不正确");
        LoginUser user=access.actor();TradeOrderDO row=orders.owned(user.getTenantId(),user.getId(),id);
        if(row==null)throw exception0(404,"本人订单不存在");paymentType(row);return row;
    }
    private AppTradeOrderPaymentRespVO paymentView(HttpServletRequest request,String store,Long id,PayOrderSubmissionRespDTO payment) {
        MemberStorefrontRespDTO entry=access.check(request,store);
        AppTradeOrderPageItemRespVO order=queries.orderAfterCommand(id);
        String state=order.getPayStatus()?"SUCCESS":order.getStatus()==40?"CLOSED":payment==null?"NOT_SUBMITTED":payment.getState();
        if(payment!=null && (Boolean.TRUE.equals(payment.getPayment().getReviewRequired())
                || PayOrderStatusEnum.isSuccess(payment.getPayment().getStatus()) && order.getStatus()!=0 && !order.getPayStatus()))state="REVIEW";
        boolean allowed=Boolean.TRUE.equals(entry.getCanPurchase()) && order.getStatus()==0 && !order.getPayStatus()
                && order.getPayPrice()>=0 && order.getServerNow()<order.getPayDeadline()
                && !java.util.Arrays.asList("CLOSING","CLOSED","REVIEW","SUCCESS").contains(state);
        AppTradeOrderPaymentRespVO result=new AppTradeOrderPaymentRespVO();result.setOrder(order);result.setPaymentState(state);result.setCanSubmit(allowed);
        result.setQrRecoveryCount(payment==null?0:payment.getQrRecoveryCount());
        result.setQrRecoveryAfter(payment==null || payment.getQrRecoveryAfter()==null?null
                :payment.getQrRecoveryAfter().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        result.setCanRecoverQr(allowed && order.getPayPrice()>0 && payment!=null && order.getPayOrderId()!=null
                && java.util.Arrays.asList("SUBMITTING","WAITING").contains(state) && payment.getQrCode()==null
                && result.getQrRecoveryCount()<3 && result.getQrRecoveryAfter()!=null);
        if(allowed && "WAITING".equals(state) && payment!=null && java.util.Objects.equals(order.getPayOrderId(),payment.getPayment().getId()))
            result.setQrCode(payment.getQrCode());
        return result;
    }

    public TradeOrderPaymentResult prepare(HttpServletRequest request,String store,Long orderId) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("支付准备必须在外层事务之外执行");
        if(orderId==null || orderId<=0 || orderId>9_007_199_254_740_991L)throw exception0(400,"订单编号不正确");
        long started=System.nanoTime();
        MemberStorefrontRespDTO entry=access.check(request,store);LoginUser user=access.actor();
        TradeOrderDO order=orders.owned(user.getTenantId(),user.getId(),orderId);
        if(order==null)throw exception0(404,"本人订单不存在");
        paymentType(order);
        TradeOrderPrepareDO preparation=preparations.progress(user.getTenantId(),orderId);
        if(preparation==null || !"CREATED".equals(preparation.getStage()) || !Objects.equals(user.getId(),preparation.getUserId()))
            throw exception0(409,"原订单尚未完成创建");
        if(Boolean.TRUE.equals(order.getPayStatus()))return result(order);
        unpaid(order);
        if(order.getPayOrderId()!=null)return result(order);
        if(!Boolean.TRUE.equals(entry.getCanPurchase()))throw exception0(403,"店铺暂不支持新购买");
        if(order.getPayPrice()==0) {
            TradeOrderPaymentResult result=write.execute(status -> bind(user,order,null,started));
            if(result==null)throw new IllegalStateException("零元订单确认未返回结果");return result;
        }

        PayOrderRespDTO payment=resources.ensurePayment(orderId);
        if(PayOrderStatusEnum.isClosed(payment.getStatus()) || Boolean.TRUE.equals(payment.getClosing()) || Boolean.TRUE.equals(payment.getReviewRequired()))
            throw exception0(409,"原支付单已停止提交，请查询订单关闭或付款进度");
        long checkedAt=System.nanoTime();MemberStorefrontRespDTO fresh=access.check(request,store);
        if(!Boolean.TRUE.equals(fresh.getCanPurchase()))throw exception0(403,"店铺暂不支持新购买");
        TradeOrderPaymentResult result=write.execute(status -> bind(user,order,payment,checkedAt));
        if(result==null)throw new IllegalStateException("支付单绑定未返回结果");return result;
    }

    private TradeOrderPaymentResult bind(LoginUser user,TradeOrderDO original,PayOrderRespDTO payment,long checkedAt) {
        recent(user,checkedAt);
        TradeOrderDO row=orders.lockOwned(user.getTenantId(),user.getId(),original.getId());
        if(row==null || !Objects.equals(original.getPayPrice(),row.getPayPrice()) || !Objects.equals(original.getPayDeadline(),row.getPayDeadline()))
            throw exception0(409,"原订单金额或期限已变化");
        if(payment!=null && row.getPayOrderId()!=null && !Objects.equals(row.getPayOrderId(),payment.getId()))
            throw exception0(409,"原订单支付单绑定不一致");
        if(Boolean.TRUE.equals(row.getPayStatus()))return result(row);
        unpaid(row);
        if(row.getPayOrderId()!=null) {
            if(payment==null || !Objects.equals(row.getPayOrderId(),payment.getId()))throw exception0(409,"原订单已经绑定其他支付单");
            return result(row);
        }
        LocalDateTime instant=now();boolean free=row.getPayPrice()==0;
        if(free && payment!=null || !free && payment==null)throw exception0(409,"原订单支付类型不一致");
        int changed=free?orders.confirmFree(user.getTenantId(),user.getId(),row.getId(),row.getAuthVersion(),instant)
                :orders.bindPayment(user.getTenantId(),user.getId(),row.getId(),payment.getId(),row.getAuthVersion(),instant);
        if(changed!=1)throw exception0(409,"原订单状态或期限已变化");
        long version=Math.addExact(row.getAuthVersion(),1L);
        if(orders.logPayment(user.getTenantId(),user.getId(),row.getId(),free?"FREE_PAY":"PAY_BIND",free?10:0,version,instant)!=1)
            throw new IllegalStateException("订单支付绑定日志保存失败");
        row.setAuthVersion(version);
        if(free){row.setStatus(10);row.setPayStatus(true);row.setPayTime(instant);}else row.setPayOrderId(payment.getId());
        recent(user,checkedAt);return result(row);
    }
    private void unpaid(TradeOrderDO order) {
        if(!Integer.valueOf(0).equals(order.getStatus()) || !Boolean.FALSE.equals(order.getPayStatus()) || order.getPayDeadline()==null
                || !now().isBefore(order.getPayDeadline()))throw exception0(409,"原订单不在有效待支付阶段");
    }
    private void paymentType(TradeOrderDO order) {
        if(Integer.valueOf(1).equals(order.getType()) && !seckillEnabled)throw exception0(503,"秒杀付款尚未启用");
        if(!java.util.Arrays.asList(0,1).contains(order.getType()))throw exception0(409,"不支持的付款订单类型");
    }
    private void recent(LoginUser user,long checkedAt) {
        LoginUser current=access.actor();
        if(!Objects.equals(current.getTenantId(),user.getTenantId()) || !Objects.equals(current.getId(),user.getId()))throw exception0(403,"当前会员已变化");
        if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"资格核对已过时，请查询原支付结果");
    }
    private TradeOrderPaymentResult result(TradeOrderDO order) {
        TradeOrderPaymentResult result=new TradeOrderPaymentResult();result.setOrderId(order.getId());result.setPayOrderId(order.getPayOrderId());
        result.setPayStatus(order.getPayStatus());result.setRequiresPayment(!Boolean.TRUE.equals(order.getPayStatus()) && order.getPayPrice()>0);return result;
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
