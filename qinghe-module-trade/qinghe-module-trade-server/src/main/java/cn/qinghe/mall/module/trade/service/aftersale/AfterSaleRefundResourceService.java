package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.coupon.dto.CouponOrderResultDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原afterCancelOrderItem/returnUsedCoupon职责，逐项核对原回执后记完成；不回滚已确认资金。
 * RPC在本地事务外，重复原售后号可恢复；一侧失败仍尝试另一侧。仅可信已授权原计划推进。
 * @author 芋道源码
 */
@Service @Slf4j
public class AfterSaleRefundResourceService {
    private final AfterSaleRefundMapper refunds;
    private final AfterSaleDecisionMapper sales;
    private final TradeOrderFulfillmentMapper orders;
    private final OrderResourceClient resources;
    private final TransactionTemplate write;
    public AfterSaleRefundResourceService(AfterSaleRefundMapper refunds,AfterSaleDecisionMapper sales,
            TradeOrderFulfillmentMapper orders,OrderResourceClient resources,PlatformTransactionManager manager) {
        this.refunds=refunds;this.sales=sales;this.orders=orders;this.resources=resources;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public AfterSaleRefundDO recover(Long id) {
        Long tenant=context(id);
        AfterSaleRefundDO plan=required(tenant,id);
        if (!Boolean.TRUE.equals(plan.getStockRecovered())) {
            try {
                Map<String,Object> receipt=resources.restoreRefundStockReceipt(id);
                mark(tenant,id,true,receipt);
            } catch (RuntimeException pending) {
                log.warn("售后库存恢复待核对，tenantId={}, afterSaleId={}",tenant,id);
            }
        }
        plan=required(tenant,id);
        if (!Boolean.TRUE.equals(plan.getCouponRecovered())) {
            try {
                CouponOrderResultDTO result=resources.returnRefundCoupon(id);
                Map<String,Object> receipt=new LinkedHashMap<>();
                receipt.put("orderId",result.getOrderId());receipt.put("couponId",result.getCouponId());receipt.put("state",result.getState());
                receipt.put("reason",result.getReturnReason());receipt.put("couponVersion",result.getQuote().getCouponVersion());
                receipt.put("discountPrice",result.getQuote().getDiscountPrice());
                mark(tenant,id,false,receipt);
            } catch (RuntimeException pending) {
                log.warn("售后原券处理待核对，tenantId={}, afterSaleId={}",tenant,id);
            }
        }
        plan=required(tenant,id);
        if (!Boolean.TRUE.equals(plan.getStockRecovered()) || !Boolean.TRUE.equals(plan.getCouponRecovered()))
            throw exception0(503,"退款已确认，库存或原券处理尚未完成，请查询原售后");
        return plan;
    }

    private void mark(Long tenant,Long id,boolean stock,Map<String,Object> receipt) {
        AfterSaleRefundDO reference=required(tenant,id);
        write.executeWithoutResult(transaction -> {
            TradeOrderDO order=orders.lock(tenant,reference.getOrderId());AfterSaleDO sale=sales.lock(tenant,id);
            AfterSaleRefundDO plan=required(tenant,id);
            if (order==null || sale==null || sale.getStatus()!=50 || !Objects.equals(sale.getRefundTime(),plan.getRefundTime())
                    || !Objects.equals(order.getUserId(),plan.getUserId()) || !Objects.equals(sale.getOrderId(),order.getId()))
                throw exception0(503,"原退款资源归属需核对");
            if (stock?Boolean.TRUE.equals(plan.getStockRecovered()):Boolean.TRUE.equals(plan.getCouponRecovered())) return;
            if (stock && plan.getRestoreCount()<=0 || !stock && !Boolean.TRUE.equals(plan.getCouponReturnRequired()))
                throw exception0(503,"原退款没有该资源处理义务");
            LocalDateTime instant=now();String json=JsonUtils.toJsonString(receipt);
            changed(stock?refunds.stockRecovered(tenant,id,json,instant):refunds.couponRecovered(tenant,id,json,instant));
            changed(refunds.resourceCase(tenant,id,sale.getAuthVersion(),instant));
            changed(refunds.resourceOrder(tenant,order.getId(),order.getAuthVersion(),instant));
            long caseVersion=Math.addExact(sale.getAuthVersion(),1),parentVersion=Math.addExact(order.getAuthVersion(),1);
            String action=stock?"SYSTEM_STOCK_RETURN":"SYSTEM_COUPON_RETURN";
            String reason=stock?"原售后库存恢复已确认":"原订单退券处理已确认";
            changed(sales.log(tenant,id,0L,"SYSTEM",action,50,50,caseVersion,instant,null,reason,false,json));
            changed(orders.log(tenant,order.getId(),0L,"SYSTEM",stock?"AFTERSALE_STOCK_RETURN":"AFTERSALE_COUPON_RETURN",
                    order.getStatus(),order.getStatus(),parentVersion,null,instant));
            AfterSaleRefundDO current=required(tenant,id);
            Map<String,Object> event=new LinkedHashMap<>();event.put("orderId",order.getId());event.put("afterSaleId",id);event.put("status",50);
            event.put("stockRecovered",current.getStockRecovered());event.put("couponRecovered",current.getCouponRecovered());
            String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();event.put("traceId",trace);
            changed(orders.event(UUID.randomUUID().toString(),tenant,order.getId(),"AFTERSALE_DECIDED",parentVersion,JsonUtils.toJsonString(event),instant));
        });
    }
    private AfterSaleRefundDO required(Long tenant,Long id) {
        AfterSaleRefundDO row=refunds.get(tenant,id);
        if (row==null || !"SUCCESS".equals(row.getStatus()) || row.getRefundTime()==null) throw exception0(409,"仅原已确认退款可恢复资源");
        return row;
    }
    private Long context(Long id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("售后资源推进不能在外层事务内");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9_007_199_254_740_991L) throw exception0(403,"售后资源恢复需要可信原租户");
        return tenant;
    }
    private void changed(int count) {if(count!=1)throw exception0(409,"原资源回执或版本已变化，请查询原售后");}
    private LocalDateTime now() {return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
