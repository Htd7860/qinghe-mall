package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.service.aftersale.AfterSaleDecisionService;
import cn.qinghe.mall.module.trade.service.aftersale.AfterSaleRefundExecutionService;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillOrderCloseService;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillCancelReleaseService;
import org.springframework.beans.factory.ObjectProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原job批量扫描→逐单既有Service职责；七个固定恢复动作，不执行任意回调。
 * 租约只管任务领取；所有业务副作用仍由既有原订单幂等/条件更新裁定。
 * @author 芋道源码
 */
@Service
@Slf4j
public class TradeOrderRecoveryService {
    private final TradeOrderRecoveryMapper records;
    private final TradeOrderRecoveryReplayMapper replays;
    private final TradeOrderMapper orders;
    private final TradeOrderPrepareMapper preparations;
    private final TradeOrderPaymentSyncService payments;
    private final TradeOrderCancelService cancellations;
    private final TradeOrderCancelResourceService cleanup;
    private final TradeOrderCreationCompensationService compensation;
    private final TradeOrderFulfillmentService fulfillment;
    private final AfterSaleDecisionService afterSales;
    private final AfterSaleRefundExecutionService refunds;
    private final ObjectProvider<TradeSeckillOrderCloseService> seckillClosures;
    private final ObjectProvider<TradeSeckillCancelReleaseService> seckillReleases;
    private final TransactionTemplate write;

    public TradeOrderRecoveryService(TradeOrderRecoveryMapper records,TradeOrderRecoveryReplayMapper replays,TradeOrderMapper orders,TradeOrderPrepareMapper preparations,
            TradeOrderPaymentSyncService payments,TradeOrderCancelService cancellations,TradeOrderCancelResourceService cleanup,
            TradeOrderCreationCompensationService compensation,TradeOrderFulfillmentService fulfillment,AfterSaleDecisionService afterSales,
            PlatformTransactionManager manager,AfterSaleRefundExecutionService refunds,ObjectProvider<TradeSeckillOrderCloseService> seckillClosures,
            ObjectProvider<TradeSeckillCancelReleaseService> seckillReleases) {
        this.records=records;this.replays=replays;this.orders=orders;this.preparations=preparations;this.payments=payments;
        this.cancellations=cancellations;this.cleanup=cleanup;this.compensation=compensation;this.fulfillment=fulfillment;
        this.afterSales=afterSales;
        this.refunds=refunds;
        this.seckillClosures=seckillClosures;
        this.seckillReleases=seckillReleases;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public Result recover(int batchSize,Long jobId,String trace) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("恢复编排不能放在外层事务内");
        if(SecurityContextHolder.getContext().getAuthentication()!=null || RequestContextHolder.getRequestAttributes()!=null)
            throw exception0(403,"恢复编排仅允许可信后台调度");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore())throw exception0(403,"恢复需要可信租户");
        if(batchSize<1 || batchSize>20 || jobId==null || jobId<=0 || trace==null
                || !trace.matches("[0-9a-f-]{36}"))throw exception0(400,"恢复批次参数不正确");
        LocalDateTime instant=now();
        boolean seckillEnabled=seckillClosures.getIfAvailable()!=null;
        // NOT EXISTS+唯一键排除已登记动作，有限批次可以继续覆盖后续订单；历史失败不会挡住发现。
        write.execute(status -> {
            records.seedPay(tenant,batchSize,instant,seckillEnabled);records.seedCancel(tenant,batchSize,instant,seckillEnabled);
            records.seedRelease(tenant,batchSize,instant,seckillEnabled);records.seedCompensate(tenant,batchSize,instant);
            records.seedReceive(tenant,batchSize,instant);
            records.seedReturn(tenant,batchSize,instant);records.reopenReturn(tenant,batchSize,instant);
            records.seedRefund(tenant,batchSize,instant);
            records.exhaustExpiredLeases(tenant,batchSize,instant);replays.expired(tenant,instant);return null;
        });
        Result result=new Result();
        try {afterSales.warnOverdue();}
        catch(RuntimeException error) {
            result.failed++;
            log.warn("售后超时提醒查询失败 tenant={}, code=503, trace={}",tenant,trace);
        }
        long started=System.nanoTime();
        for(int index=0;index<batchSize && System.nanoTime()-started<45_000_000_000L;index++) {
            TradeOrderRecoveryDO row=claim(tenant,jobId,trace);
            if(row==null)break;
            result.attempted++;
            MDC.put("orderId",row.getOrderId().toString());
            try {
                boolean done=perform(row);
                // 正常等待付款/售后暂停不是异常，归还本次预算；失败/执行中崩溃才消耗次数。
                finish(row,done?"DONE":"READY",done?now():now().plusSeconds(15),done?0:1,null);
                if(done)result.completed++;else result.pending++;
            } catch(RuntimeException error) {
                int code=error instanceof ServiceException && Arrays.asList(400,401,403,404,409,429,500,503).contains(((ServiceException)error).getCode())
                        ?((ServiceException)error).getCode():503;
                String state=row.getAttempts()>=10?"MANUAL":"READY";
                long delay=Math.min(900L,15L*(1L<<(row.getAttempts()-1)));
                finish(row,state,now().plusSeconds(delay),0,code);
                result.failed++;
                log.warn("订单恢复失败 tenant={}, order={}, kind={}, attempt={}, code={}, trace={}",tenant,row.getOrderId(),row.getKind(),row.getAttempts(),code,trace);
            } finally {MDC.remove("orderId");}
        }
        result.manual=records.manualCount(tenant);
        if(result.manual>0)log.error("订单恢复待人工处理 tenant={}, count={}, trace={}",tenant,result.manual,trace);
        return result;
    }

    /** MQ到期提醒在消费事务提交后触发；与XXL共用同一领取/次数/租约及业务取消路径。 */
    public void recoverExpiry(Long orderId,String trace) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("到期恢复仅限事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || orderId==null || orderId<=0 || orderId>9_007_199_254_740_991L || trace==null || !trace.matches("[0-9a-f-]{36}"))
            throw exception0(403,"到期恢复上下文不正确");
        TradeOrderRecoveryDO row=claim(tenant,null,trace,orderId);
        // 未到期、另一执行者持有租约、已完成或MANUAL均不越过原条件。
        if(row==null)return;
        try {
            boolean done=perform(row);
            finish(row,done?"DONE":"READY",done?now():now().plusSeconds(15),done?0:1,null);
        } catch(RuntimeException error) {
            long delay=Math.min(900L,15L*(1L<<(row.getAttempts()-1)));
            finish(row,row.getAttempts()>=10?"MANUAL":"READY",now().plusSeconds(delay),0,503);
            log.warn("到期消息业务恢复待核对 tenant={}, order={}, event={}, attempt={}, code=503",tenant,orderId,trace,row.getAttempts());
        }
    }

    private TradeOrderRecoveryDO claim(Long tenant,Long jobId,String trace) {
        return claim(tenant,jobId,trace,null);
    }
    private TradeOrderRecoveryDO claim(Long tenant,Long jobId,String trace,Long expiryOrder) {
        return write.execute(status -> {
            LocalDateTime instant=now();TradeOrderRecoveryDO row=expiryOrder==null?records.next(tenant,instant):records.nextExpiry(tenant,expiryOrder,instant);
            if(row==null)return null;
            String owner=UUID.randomUUID().toString();
            if(records.claim(tenant,row.getId(),row.getAuthVersion(),owner,instant.plusSeconds(180),jobId,trace,instant)!=1)
                throw exception0(409,"恢复任务领取版本已变化");
            if(row.getActiveReplayId()!=null && replays.progress(tenant,row.getId(),row.getActiveReplayId(),row.getAuthVersion(),
                    "RUNNING",row.getAttempts()+1,row.getLastErrorCode(),jobId,trace,instant)!=1)
                throw new IllegalStateException("人工重放领取审计缺失");
            row.setLeaseOwner(owner);row.setAuthVersion(Math.addExact(row.getAuthVersion(),1));row.setAttempts(row.getAttempts()+1);
            row.setLastJobId(jobId);row.setTraceId(trace);
            return row;
        });
    }
    private boolean perform(TradeOrderRecoveryDO row) {
        if("CREATE_COMPENSATE".equals(row.getKind())) {
            TradeOrderPrepareDO preparation=preparations.progress(row.getTenantId(),row.getOrderId());
            if(preparation==null)throw exception0(409,"原订单准备记录缺失");
            if("CREATED".equals(preparation.getStage()) || "FAILED".equals(preparation.getStage()))return true;
            compensation.compensate(row.getOrderId());return true;
        }
        TradeOrderDO order=orders.paymentOrder(row.getTenantId(),row.getOrderId());
        if(order==null)throw exception0(409,"原正式订单缺失");
        switch(row.getKind()) {
            case "PAY_SYNC":
                if(order.getStatus()!=0 || Boolean.TRUE.equals(order.getPayStatus()))return true;
                if(Integer.valueOf(1).equals(order.getType())) {
                    requireSeckillClose();return payments.synchronizeSeckillPaid(row.getOrderId());
                }
                return payments.synchronizePaid(row.getOrderId());
            case "CANCEL_EXPIRED":
                if(order.getStatus()!=0 || Boolean.TRUE.equals(order.getPayStatus()))return true;
                if(Integer.valueOf(1).equals(order.getType())) {
                    requireSeckillClose().closeExpired(row.getOrderId());return true;
                }
                cancellations.cancelExpired(row.getOrderId());return true;
            case "CANCEL_RELEASE":
                if(Integer.valueOf(1).equals(order.getType())) {
                    TradeSeckillCancelReleaseService release=seckillReleases.getIfAvailable();
                    if(release==null)throw exception0(503,"秒杀资源确认未启用，请核查原恢复记录");
                    return release.release(row.getOrderId());
                }
                cleanup.release(row.getOrderId());return true;
            case "AUTO_RECEIVE":
                // 人工收货已完成或后续全额退款已关闭，无需伪造新的收货事实。
                if(order.getStatus()==30 || order.getStatus()==40)return true;
                if(order.getStatus()!=20)throw exception0(409,"原订单不在已发货阶段，请核对历史");
                // 领取后可能新受理售后；原订单父锁下再次检查，不依赖扫描时的状态。
                return fulfillment.receiveBySystem(row.getOrderId());
            case "RETURN_EXPIRE":
                return afterSales.expireReturnByOrder(row.getOrderId());
            case "REFUND":
                if (row.getAfterSaleId()==null)throw exception0(409,"原退款恢复缺少售后目标");
                return refunds.recover(row.getOrderId(),row.getAfterSaleId());
            default:throw new IllegalStateException("不支持的订单恢复动作");
        }
    }
    private void finish(TradeOrderRecoveryDO row,String state,LocalDateTime available,int refundAttempt,Integer error) {
        Integer changed=write.execute(status -> {
            LocalDateTime instant=now();
            int count=records.finish(row.getTenantId(),row.getId(),row.getLeaseOwner(),row.getAuthVersion(),state,available,refundAttempt,error,instant);
            if(count==1 && row.getActiveReplayId()!=null && replays.progress(row.getTenantId(),row.getId(),row.getActiveReplayId(),row.getAuthVersion(),
                    state,row.getAttempts()-refundAttempt,error,row.getLastJobId(),row.getTraceId(),instant)!=1)
                throw new IllegalStateException("人工重放结果审计缺失");
            return count;
        });
        if(changed==null || changed!=1)throw exception0(503,"恢复租约或提交结果待确认，不能覆盖其他执行者");
    }
    private TradeSeckillOrderCloseService requireSeckillClose() {
        TradeSeckillOrderCloseService service=seckillClosures.getIfAvailable();
        if(service==null)throw exception0(503,"秒杀后台生命周期尚未启用，请核查原恢复记录");
        return service;
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
    @Data
    public static class Result {private int attempted;private int completed;private int pending;private int failed;private long manual;}
}
