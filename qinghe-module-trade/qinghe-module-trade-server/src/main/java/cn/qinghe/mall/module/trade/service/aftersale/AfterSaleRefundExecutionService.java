package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.refund.dto.PayRefundResultDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient.RefundAction;
import cn.qinghe.mall.module.trade.service.aftersale.dto.AfterSaleRefundBalanceDTO;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原createPayRefund/updateAfterSaleRefunded职责，只推进已授权且不可撤销的原计划。
 * 仅可信内部编排/后续受保护任务调用；没有接收金额或SUCCESS DTO的外部入口。
 * 当前返回内部记录含原因，不能直接作为Controller返回。资金确认与资源恢复分别记录。
 * @author 芋道源码
 */
@Service
public class AfterSaleRefundExecutionService {
    private final AfterSaleRefundMapper refunds;
    private final AfterSaleDecisionMapper decisions;
    private final AfterSaleMapper sales;
    private final TradeOrderFulfillmentMapper orders;
    private final OrderResourceClient resources;
    private final AfterSaleRefundResourceService recovery;
    private final TransactionTemplate write;

    public AfterSaleRefundExecutionService(AfterSaleRefundMapper refunds, AfterSaleDecisionMapper decisions,
            AfterSaleMapper sales, TradeOrderFulfillmentMapper orders, OrderResourceClient resources,
            PlatformTransactionManager manager, AfterSaleRefundResourceService recovery) {
        this.refunds=refunds;this.decisions=decisions;this.sales=sales;this.orders=orders;this.resources=resources;this.recovery=recovery;
        write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
    }

    /** READ不推进。其他动作仅按原计划调用，未知留45；零金额仍须完成原单位与资源义务。 */
    public AfterSaleRefundDO advance(Long id, RefundAction action) {
        Long tenant=context(id);
        if (action==null) throw exception0(400,"请提供固定退款动作");
        AfterSaleRefundDO plan=required(tenant,id);
        if (action==RefundAction.READ) return plan;
        if (plan.getExecutionRequestedAt()==null) {
            if (action==RefundAction.QUERY) return plan; // 同步不等于财务确认执行，含零金额计划。
            throw exception0(409,"请先确认执行原退款");
        }
        if ("SUCCESS".equals(plan.getStatus())) return recovery.recover(id);
        if (plan.getTotalPrice()==0) {confirm(tenant,id,null);return recovery.recover(id);}
        PayRefundResultDTO result;
        if (action==RefundAction.SUBMIT || action==RefundAction.RETRY || action==RefundAction.REGISTER) {
            result=resources.refundPayment(id,RefundAction.REGISTER);
            if (!"SUCCESS".equals(result.getStatus()) && action!=RefundAction.REGISTER) {
                result=resources.refundPayment(id,action);
            }
        } else {
            result=resources.refundPayment(id,RefundAction.READ);
            if (result==null) return plan; // 不把暂未查到当作可换号/撤销或没有在途登记的证明。
            if (!"SUCCESS".equals(result.getStatus())) result=resources.refundPayment(id,RefundAction.QUERY);
        }
        if ("SUCCESS".equals(result.getStatus())) {confirm(tenant,id,result);return recovery.recover(id);}
        PayRefundResultDTO remote=result;
        return write.execute(transaction -> {
            TradeOrderDO order=orders.lock(tenant,plan.getOrderId());
            AfterSaleDO sale=decisions.lock(tenant,id);
            AfterSaleRefundDO current=required(tenant,id);
            owned(order,sale,current);
            if ("SUCCESS".equals(current.getStatus())) return current;
            if (sale.getStatus()!=45) throw exception0(409,"原退款阶段需核对");
            match(current,remote,order);
            refunds.progress(tenant,id,remote.getId(),remote.getAuthVersion(),remote.getStatus(),now());
            return required(tenant,id); // 旧版本回包的条件更新为0时保留较新的原进度。
        });
    }

    /** 仅既有租户任务恢复原已确认执行的售后，不自动重新提交FAILED/UNKNOWN。 */
    public boolean recover(Long orderId,Long id) {
        if (SecurityContextHolder.getContext().getAuthentication()!=null || RequestContextHolder.getRequestAttributes()!=null)
            throw exception0(403,"退款恢复仅允许可信后台调度");
        Long tenant=context(id);AfterSaleRefundDO plan=required(tenant,id);
        if (!Objects.equals(plan.getOrderId(),orderId) || plan.getExecutionRequestedAt()==null)
            throw exception0(409,"原退款恢复目标或执行确认需核对");
        AfterSaleRefundDO result=advance(id,"PREPARED".equals(plan.getStatus())?RefundAction.SUBMIT:RefundAction.QUERY);
        if (!"SUCCESS".equals(result.getStatus()) || !Boolean.TRUE.equals(result.getStockRecovered())
                || !Boolean.TRUE.equals(result.getCouponRecovered())) throw exception0(503,"原退款资金或资源仍待确认");
        return true;
    }

    /** 只能从本类已核对的原RPC成功或本地零金额计划进入；不向HTTP暴露。 */
    private AfterSaleRefundDO confirm(Long tenant,Long id,PayRefundResultDTO payment) {
        AfterSaleRefundDO reference=required(tenant,id);
        return write.execute(transaction -> {
            TradeOrderDO order=orders.lock(tenant,reference.getOrderId());
            AfterSaleDO sale=decisions.lock(tenant,id);
            AfterSaleRefundDO plan=required(tenant,id);
            owned(order,sale,plan);
            if ("SUCCESS".equals(plan.getStatus())) {
                if (sale.getStatus()!=50 || !Objects.equals(plan.getRefundTime(),sale.getRefundTime()))
                    throw exception0(503,"原退款成功事实需核对");
                return plan;
            }
            if (sale.getStatus()!=45 || !Boolean.TRUE.equals(order.getPayStatus())
                    || !Arrays.asList(10,20,30).contains(order.getStatus())
                    || sale.getAuthVersion()<plan.getAfterSaleVersion()) throw exception0(409,"原待确认退款阶段不一致");
            LocalDateTime instant=now();
            LocalDateTime refunded;
            if (plan.getTotalPrice()==0) {
                if (payment!=null || plan.getGoodsPrice()!=0 || plan.getDeliveryPrice()!=0) throw exception0(503,"零金额计划不完整");
                refunded=instant;
            } else {
                if (payment==null || !"SUCCESS".equals(payment.getStatus()) || payment.getSuccessTime()==null)
                    throw exception0(409,"原退款尚未确认到账");
                match(plan,payment,order);
                refunded=payment.getSuccessTime();
            }
            List<TradeOrderItemDO> items=orders.items(tenant,order.getId());
            AfterSaleRefundBalanceDTO.Totals totals=AfterSaleRefundCalculator.verifyBalances(order,items,
                    refunds.unitBalances(tenant,order.getId()),refunds.balance(tenant,order.getId()));
            TradeOrderItemDO item=items.stream().filter(value -> Objects.equals(value.getId(),plan.getOrderItemId())).findFirst().orElse(null);
            if (item==null || !Objects.equals(plan.getGoodsPrice(),sale.getGoodsRefundPrice())
                    || !Objects.equals(plan.getTotalPrice(),Math.addExact(plan.getGoodsPrice(),plan.getDeliveryPrice()))
                    || !Objects.equals(order.getDeliveryRefundReserved(),plan.getDeliveryPrice())
                    || sales.pending(tenant,order.getId())!=order.getAfterSalePendingCount())
                throw exception0(503,"原退款单位、运费占用或售后计数不一致");
            List<AfterSaleUnitDO> units=decisions.units(tenant,id);
            AfterSaleRefundCalculator.verifyReservedUnits(sale,units);
            AfterSaleRefundCalculator.verifyOriginalUnits(item,units);
            boolean shipped=order.getDeliveryTime()!=null;
            int restore=shipped ? sale.getWay()==20 ? sale.getSalableCount() : 0 : sale.getCount();
            if (!Objects.equals(plan.getShipped(),shipped) || !Objects.equals(plan.getRestoreCount(),restore)
                    || sale.getWay()==20 && (sale.getReceiveTime()==null || !Objects.equals(sale.getReceivedCount(),sale.getCount())))
                throw exception0(503,"原实物验收或回补计划不一致");
            boolean closed=Math.addExact(totals.getRefundedQuantity(),sale.getCount().longValue())==order.getProductCount().longValue();
            if (closed && order.getAfterSalePendingCount()!=1) throw exception0(503,"原整单仍有其他售后义务需核对");
            long expectedFreight=(!shipped && closed || shipped && Boolean.TRUE.equals(sale.getQualityApproved()))
                    ? Math.subtractExact(order.getDeliveryPrice(),order.getDeliveryRefundPrice()) : 0L;
            boolean expectedCoupon=order.getCouponId()!=null && closed
                    && Math.addExact(order.getRefundPrice(),plan.getTotalPrice())==order.getPayPrice();
            if (!Objects.equals(plan.getDeliveryPrice(),expectedFreight) || !Objects.equals(plan.getCouponReturnRequired(),expectedCoupon))
                throw exception0(503,"原运费或退券依据与冻结计划不一致");
            Long payId=payment==null?null:payment.getId();
            Long payVersion=payment==null?null:payment.getAuthVersion();
            changed(refunds.success(tenant,id,payId,payVersion,refunded,instant));
            if (refunds.refundUnits(tenant,id)!=sale.getCount()) throw exception0(409,"原退款单位阶段已变化");
            changed(refunds.refundItem(tenant,order.getId(),item.getId(),item.getRefundCount(),sale.getCount()));
            changed(refunds.finish(tenant,id,sale.getAuthVersion(),refunded,instant));
            changed(refunds.confirmOrder(tenant,order.getId(),order.getAuthVersion(),plan.getTotalPrice(),plan.getDeliveryPrice(),closed,instant));
            Map<String,Object> detail=new LinkedHashMap<>();
            detail.put("refundNo",plan.getRefundNo());detail.put("payRefundId",payId);detail.put("totalPrice",plan.getTotalPrice());
            detail.put("deliveryPrice",plan.getDeliveryPrice());detail.put("zeroAmount",payment==null);
            long caseVersion=Math.addExact(sale.getAuthVersion(),1),parentVersion=Math.addExact(order.getAuthVersion(),1);
            changed(decisions.log(tenant,id,0L,"SYSTEM","SYSTEM_REFUND_SUCCESS",45,50,caseVersion,instant,
                    null,"原退款结果已确认",false,JsonUtils.toJsonString(detail)));
            changed(orders.log(tenant,order.getId(),0L,"SYSTEM","AFTERSALE_REFUND_SUCCESS",order.getStatus(),closed?40:order.getStatus(),parentVersion,null,instant));
            Map<String,Object> event=new LinkedHashMap<>();event.put("orderId",order.getId());event.put("afterSaleId",id);
            event.put("status",50);event.put("refundNo",plan.getRefundNo());event.put("payRefundId",payId);
            String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();
            event.put("traceId",trace);
            changed(orders.event(UUID.randomUUID().toString(),tenant,order.getId(),"AFTERSALE_DECIDED",parentVersion,JsonUtils.toJsonString(event),instant));
            // stock_recovered/coupon_recovered只标记确无该项义务的情况；实际跨服务回补另行幂等推进。
            return required(tenant,id);
        });
    }

    private void owned(TradeOrderDO order,AfterSaleDO sale,AfterSaleRefundDO plan) {
        if (order==null || sale==null || !Objects.equals(order.getId(),sale.getOrderId())
                || !Objects.equals(order.getId(),plan.getOrderId()) || !Objects.equals(order.getUserId(),sale.getUserId())
                || !Objects.equals(order.getUserId(),plan.getUserId()) || !Objects.equals(order.getPayOrderId(),plan.getPayOrderId())
                || !Objects.equals(sale.getOrderItemId(),plan.getOrderItemId())) throw exception0(503,"原退款归属不完整");
    }

    private void match(AfterSaleRefundDO plan,PayRefundResultDTO payment,TradeOrderDO order) {
        if (!Objects.equals(plan.getTenantId(),payment.getTenantId()) || !"qinghe-trade".equals(payment.getAppKey())
                || !Objects.equals(plan.getPayOrderId(),payment.getOrderId()) || !Objects.equals(plan.getUserId(),payment.getUserId())
                || !plan.getOrderId().toString().equals(payment.getMerchantOrderId()) || !plan.getAfterSaleId().toString().equals(payment.getMerchantRefundId())
                || !plan.getRefundNo().equals(payment.getNo()) || !Objects.equals(plan.getTotalPrice(),payment.getRefundPrice())
                || !Objects.equals(order.getPayPrice(),payment.getPayPrice())
                || !Integer.valueOf(1).equals(payment.getUserType())
                || plan.getPayRefundId()!=null && !Objects.equals(plan.getPayRefundId(),payment.getId()))
            throw exception0(503,"原退款回包与当前计划不一致，请核对原号");
    }

    private AfterSaleRefundDO required(Long tenant,Long id) {
        AfterSaleRefundDO row=refunds.get(tenant,id);
        if (row==null) throw exception0(404,"原不可撤销退款计划不存在");
        return row;
    }

    private Long context(Long id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("退款推进不能在外层事务中执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9_007_199_254_740_991L) throw exception0(403,"原退款推进需要可信租户");
        return tenant;
    }
    private void changed(int count) {if(count!=1)throw exception0(409,"原退款事实或版本已变化，请查询原结果");}
    private LocalDateTime now() {return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
