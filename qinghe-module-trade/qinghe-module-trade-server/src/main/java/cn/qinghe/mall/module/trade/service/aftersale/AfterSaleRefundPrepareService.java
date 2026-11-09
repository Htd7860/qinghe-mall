package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.service.aftersale.dto.*;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原refundAfterSale资格/原退款号/退款计划职责，先提交不可撤销计划，不在本地锁内RPC。
 * 本方法不调用渠道或确认成功；PREPARED不代表已退款、恢复库存或退券。
 * @author 芋道源码
 */
@Service
public class AfterSaleRefundPrepareService {
    private final AfterSaleRefundMapper refunds;
    private final AfterSaleDecisionMapper decisions;
    private final AfterSaleMapper sales;
    private final TradeOrderFulfillmentMapper orders;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final TransactionTemplate write;
    public AfterSaleRefundPrepareService(AfterSaleRefundMapper refunds,AfterSaleDecisionMapper decisions,AfterSaleMapper sales,
            TradeOrderFulfillmentMapper orders,SecurityFrameworkService security,Validator validator,PlatformTransactionManager manager) {
        this.refunds=refunds;this.decisions=decisions;this.sales=sales;this.orders=orders;this.security=security;this.validator=validator;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public AfterSaleRefundPlanDTO prepare(AfterSaleRefundPrepareCommand body) {
        if(body==null || !validator.validate(body).isEmpty() || body.getReason().trim().isEmpty() || body.getReason().chars().anyMatch(Character::isISOControl))
            throw exception0(400,"请填写有效退款准备命令及原因");
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("退款准备入口不能放在外层事务内");
        LoginUser actor=current();if(!security.hasPermission("trade:after-sale:refund"))throw exception0(403,"没有资金退款执行权限");
        long checkedAt=System.nanoTime();String reason=body.getReason().trim();
        AfterSaleDO reference=decisions.get(actor.getTenantId(),body.getId());if(reference==null)throw exception0(404,"本租户售后不存在");
        try {
            return write.execute(transaction -> {
                fresh(actor,checkedAt);TradeOrderDO order=orders.lock(actor.getTenantId(),reference.getOrderId());fresh(actor,checkedAt);
                AfterSaleDO row=decisions.lock(actor.getTenantId(),body.getId());
                if(order==null || row==null || !Objects.equals(row.getOrderId(),order.getId()) || !Objects.equals(row.getUserId(),order.getUserId()))
                    throw exception0(503,"售后与原订单归属不完整");
                AfterSaleRefundDO old=refunds.get(actor.getTenantId(),row.getId());
                if(old!=null){same(old,actor,body,reason);fresh(actor,checkedAt);return result(old);}
                if(row.getStatus()!=40 || row.getAuditUserId()==null || !Objects.equals(row.getAuthVersion(),body.getExpectedVersion())
                        || !Boolean.TRUE.equals(order.getPayStatus()) || !Arrays.asList(10,20,30).contains(order.getStatus()))
                    throw exception0(409,"仅已审核且未进入资金处理的售后可准备退款");
                if(refunds.inFlight(actor.getTenantId(),order.getId())!=null)throw exception0(409,"原订单有退款结果待确认，请先核对原退款号");
                int pending=sales.pending(actor.getTenantId(),order.getId());
                if(pending<1 || !Integer.valueOf(pending).equals(order.getAfterSalePendingCount()))throw exception0(503,"原订单售后计数需核对");
                List<TradeOrderItemDO> items=orders.items(actor.getTenantId(),order.getId());
                AfterSaleRefundBalanceDTO.Totals totals=balances(order,items);
                TradeOrderItemDO item=items.stream().filter(value -> Objects.equals(value.getId(),row.getOrderItemId())).findFirst().orElse(null);
                if(item==null)throw exception0(503,"原售后商品快照缺失");
                List<AfterSaleUnitDO> units=decisions.units(actor.getTenantId(),row.getId());
                AfterSaleRefundCalculator.verifyReservedUnits(row,units);AfterSaleRefundCalculator.verifyOriginalUnits(item,units);
                boolean shipped=order.getDeliveryTime()!=null;
                if ((order.getStatus()==10 && shipped) || (Arrays.asList(20,30).contains(order.getStatus()) && !shipped))
                    throw exception0(503,"原订单发货事实不完整");
                if(row.getWay()==20 && (!shipped || row.getDeliveryTime()==null || row.getReceiveTime()==null || !Objects.equals(row.getReceivedCount(),row.getCount())))
                    throw exception0(409,"退货尚未完成验收，不能进入资金处理");
                boolean allUnits = Math.addExact(totals.getRefundedQuantity(), row.getCount().longValue())
                        == order.getProductCount().longValue();
                long freight = refundDeliveryPrice(order, row, allUnits);
                long total = Math.addExact(row.getGoodsRefundPrice(), freight);
                if(total>0 && order.getPayOrderId()==null)throw exception0(503,"原实付支付账缺失，不能准备资金退款");
                if(Math.addExact(Math.addExact(order.getRefundPrice(),totals.getReservedGoods()),freight)>order.getPayPrice())throw exception0(409,"整单退款和处理中占用超过原实付");
                LocalDateTime instant=now();AfterSaleRefundDO plan=new AfterSaleRefundDO();
                plan.setTenantId(actor.getTenantId());plan.setAfterSaleId(row.getId());plan.setOrderId(order.getId());plan.setOrderItemId(item.getId());
                plan.setUserId(order.getUserId());plan.setPayOrderId(order.getPayOrderId());plan.setRequestId(body.getRequestId());plan.setRefundNo("RF"+UUID.randomUUID().toString().replace("-",""));
                plan.setActorId(actor.getId());plan.setReason(reason);plan.setAfterSaleVersion(Math.addExact(row.getAuthVersion(),1));plan.setStatus("PREPARED");
                plan.setGoodsPrice(row.getGoodsRefundPrice());plan.setDeliveryPrice(freight);plan.setTotalPrice(total);plan.setShipped(shipped);
                plan.setRestoreCount(!shipped?row.getCount():row.getWay()==20?row.getSalableCount():0);
                plan.setCouponReturnRequired(order.getCouponId()!=null && allUnits && Math.addExact(order.getRefundPrice(),total)==order.getPayPrice());
                plan.setCreateTime(instant);plan.setUpdateTime(instant);
                if(refunds.insert(plan)!=1 || refunds.begin(actor.getTenantId(),row.getId(),row.getAuthVersion(),instant)!=1
                        || refunds.reserve(actor.getTenantId(),order.getId(),order.getAuthVersion(),freight,totals.getReservedGoods(),instant)!=1)
                    throw exception0(409,"原退款计划或订单阶段已变化");
                Map<String,Object> details=new LinkedHashMap<>();details.put("refundNo",plan.getRefundNo());details.put("goodsPrice",plan.getGoodsPrice());
                details.put("deliveryPrice",freight);details.put("totalPrice",total);details.put("restoreCount",plan.getRestoreCount());details.put("couponReturnRequired",plan.getCouponReturnRequired());
                long parentNext=Math.addExact(order.getAuthVersion(),1);
                if(decisions.log(actor.getTenantId(),row.getId(),actor.getId(),"ADMIN","ADMIN_REFUND_PREPARE",40,45,plan.getAfterSaleVersion(),instant,body.getRequestId(),reason,false,JsonUtils.toJsonString(details))!=1
                        || orders.log(actor.getTenantId(),order.getId(),actor.getId(),"ADMIN","AFTERSALE_REFUND_PREPARE",order.getStatus(),order.getStatus(),parentNext,null,instant)!=1)
                    throw new IllegalStateException("原退款准备日志保存失败");
                Map<String,Object> event=new LinkedHashMap<>();event.put("orderId",order.getId());event.put("afterSaleId",row.getId());event.put("status",45);event.put("refundNo",plan.getRefundNo());
                String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();event.put("traceId",trace);
                if(orders.event(UUID.randomUUID().toString(),actor.getTenantId(),order.getId(),"AFTERSALE_DECIDED",parentNext,JsonUtils.toJsonString(event),instant)!=1)
                    throw new IllegalStateException("退款准备待发送事件保存失败");
                fresh(actor,checkedAt);return result(plan);
            });
        } catch(DuplicateKeyException conflict) {
            fresh(actor,checkedAt);AfterSaleRefundDO old=refunds.request(actor.getTenantId(),body.getRequestId());
            if(old==null)throw exception0(409,"原退款号或并发准备记录需查询确认");same(old,actor,body,reason);return result(old);
        }
    }
    /** 已核对原订单余额；未发货最后一批退余运费，已发货须有质量问题审核结论。 */
    private long refundDeliveryPrice(TradeOrderDO order, AfterSaleDO afterSale, boolean allUnits) {
        long remaining = Math.subtractExact(order.getDeliveryPrice(), order.getDeliveryRefundPrice());
        if (order.getDeliveryTime() == null) {
            return allUnits ? remaining : 0L;
        }
        return Boolean.TRUE.equals(afterSale.getQualityApproved()) ? remaining : 0L;
    }
    private AfterSaleRefundBalanceDTO.Totals balances(TradeOrderDO order,List<TradeOrderItemDO> items) {
        AfterSaleRefundBalanceDTO.Totals totals = AfterSaleRefundCalculator.verifyBalances(order,items,
                refunds.unitBalances(order.getTenantId(),order.getId()),refunds.balance(order.getTenantId(),order.getId()));
        if (order.getDeliveryRefundReserved()!=0) throw exception0(503,"原订单有运费退款待确认");
        return totals;
    }

    private void same(AfterSaleRefundDO old,LoginUser actor,AfterSaleRefundPrepareCommand body,String reason) {
        if(!Objects.equals(old.getAfterSaleId(),body.getId()) || !Objects.equals(old.getRequestId(),body.getRequestId()) || !Objects.equals(old.getActorId(),actor.getId())
                || !Objects.equals(old.getReason(),reason) || !Objects.equals(old.getAfterSaleVersion(),Math.addExact(body.getExpectedVersion(),1)))
            throw exception0(409,"原退款已准备，请查询原退款号，不要另建申请");
    }
    private AfterSaleRefundPlanDTO result(AfterSaleRefundDO row) {return new AfterSaleRefundPlanDTO(row.getAfterSaleId(),row.getRefundNo(),row.getAfterSaleVersion(),row.getGoodsPrice(),row.getDeliveryPrice(),row.getTotalPrice(),row.getRestoreCount(),row.getCouponReturnRequired());}
    private LoginUser current() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L
                || actor.getTenantId()==null || actor.getTenantId()<=0 || actor.getTenantId()>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || !Objects.equals(TenantContextHolder.getTenantId(),actor.getTenantId()))throw exception0(403,"仅当前商家财务可准备退款");return actor;
    }
    private void fresh(LoginUser actor,long checkedAt) {LoginUser value=current();if(!Objects.equals(value.getId(),actor.getId()) || !Objects.equals(value.getTenantId(),actor.getTenantId()) || System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"退款身份核对已过时，请查询原请求");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
