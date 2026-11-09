package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundCommandReqVO;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundRespVO;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundRetryReqVO;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundRetryResultRespVO;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundContinuationReqVO;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundBudgetRespVO;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.AfterSaleRefundContinuationResultRespVO;
import cn.qinghe.mall.module.pay.api.refund.dto.PayRefundContinuationResultDTO;
import cn.qinghe.mall.module.pay.api.refund.dto.PayRefundResultDTO;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleLogDO;
import org.springframework.dao.DuplicateKeyException;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleRefundDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleDecisionMapper;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleRefundMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient.RefundAction;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原后台refundAfterSale委托职责，复用现有原计划/支付查询/资源恢复。
 * 只读入口不推进；RPC在只读快照事务结束后执行，不能把SUCCESS当作资源全部完成。
 * @author 芋道源码
 */
@Service
public class AfterSaleRefundAdminService {
    private final AfterSaleRefundExecutionService execution;
    private final AfterSaleRefundMapper refunds;
    private final AfterSaleDecisionMapper decisions;
    private final TradeOrderMapper orders;
    private final TradeOrderFulfillmentMapper fulfillment;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final OrderResourceClient resources;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public AfterSaleRefundAdminService(AfterSaleRefundExecutionService execution, AfterSaleRefundMapper refunds,
            AfterSaleDecisionMapper decisions, TradeOrderMapper orders, SecurityFrameworkService security,
            Validator validator, PlatformTransactionManager manager, TradeOrderFulfillmentMapper fulfillment,OrderResourceClient resources) {
        this.execution=execution;this.refunds=refunds;this.decisions=decisions;this.orders=orders;
        this.security=security;this.validator=validator;
        this.fulfillment=fulfillment;
        this.resources=resources;
        read=new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);read.setTimeout(5);
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** 固定SUBMIT：支付侧只首次提交PREPARED，重复原请求不会绕过提交次数/间隔。 */
    public AfterSaleRefundRespVO execute(AfterSaleRefundCommandReqVO body) {
        return advance(body,RefundAction.SUBMIT);
    }

    /** 显式POST才查询渠道并确认/恢复；不因查到FAILED/UNKNOWN换新退款号或再次提交。 */
    public AfterSaleRefundRespVO synchronize(AfterSaleRefundCommandReqVO body) {
        return advance(body,RefundAction.QUERY);
    }

    /** 原UUID只允许一次重试调用；重复读取原回执对应的当前进度，不再次调用支付。 */
    public AfterSaleRefundRespVO retry(AfterSaleRefundRetryReqVO body) {
        if (body==null || !validator.validate(body).isEmpty() || body.getReason().trim().isEmpty()
                || body.getReason().chars().anyMatch(Character::isISOControl)) throw exception0(400,"请提供原号、版本、请求号和重试原因");
        long checkedAt=System.nanoTime();LoginUser actor=current();
        AfterSaleRefundRespVO reference=snapshot(actor,body.getId(),body.getRefundNo());
        String reason=body.getReason().trim();
        boolean claimed;
        try {
            claimed=Boolean.TRUE.equals(write.execute(transaction -> {
                freshExecution(actor,checkedAt);
                TradeOrderDO order=fulfillment.lock(actor.getTenantId(),reference.getOrderId());
                AfterSaleDO sale=decisions.lock(actor.getTenantId(),body.getId());
                AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),body.getId());
                if (order==null || sale==null || plan==null || !Objects.equals(plan.getRefundNo(),body.getRefundNo())
                        || !Objects.equals(plan.getOrderId(),order.getId()) || !Objects.equals(sale.getOrderId(),order.getId())
                        || !Objects.equals(plan.getUserId(),order.getUserId()) || !Objects.equals(sale.getUserId(),order.getUserId())
                        || !Objects.equals(plan.getOrderItemId(),sale.getOrderItemId())) throw exception0(503,"原退款重试归属需核对");
                AfterSaleLogDO previous=decisions.receipt(actor.getTenantId(),body.getRequestId());
                if (previous!=null) {sameRetry(previous,actor,body,reason);return false;}
                if (sale.getStatus()!=45 || plan.getExecutionRequestedAt()==null || plan.getPayRefundId()==null
                        || !Arrays.asList("PROCESSING","UNKNOWN","FAILED").contains(plan.getStatus())
                        || !Objects.equals(sale.getAuthVersion(),body.getExpectedVersion())
                        || !Boolean.TRUE.equals(order.getPayStatus()) || !Arrays.asList(10,20,30).contains(order.getStatus()))
                    throw exception0(409,"请先同步原退款，并核对当前阶段与版本后重试");
                LocalDateTime instant=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
                if (refunds.executionCase(actor.getTenantId(),sale.getId(),sale.getAuthVersion(),instant)!=1
                        || refunds.executionOrder(actor.getTenantId(),order.getId(),order.getAuthVersion(),instant)!=1)
                    throw exception0(409,"原退款重试版本已变化");
                Map<String,Object> detail=new LinkedHashMap<>();detail.put("refundNo",plan.getRefundNo());
                detail.put("afterSaleId",sale.getId());detail.put("totalPrice",plan.getTotalPrice());
                if (decisions.log(actor.getTenantId(),sale.getId(),actor.getId(),"ADMIN","ADMIN_REFUND_RETRY",45,45,
                        Math.addExact(sale.getAuthVersion(),1),instant,body.getRequestId(),reason,false,JsonUtils.toJsonString(detail))!=1
                        || fulfillment.log(actor.getTenantId(),order.getId(),actor.getId(),"ADMIN","AFTERSALE_REFUND_RETRY",
                        order.getStatus(),order.getStatus(),Math.addExact(order.getAuthVersion(),1),JsonUtils.toJsonString(detail),instant)!=1)
                    throw new IllegalStateException("原退款重试审计保存失败");
                detail.put("orderId",order.getId());detail.put("status",45);detail.put("retryRequested",true);
                String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();detail.put("traceId",trace);
                if (fulfillment.event(UUID.randomUUID().toString(),actor.getTenantId(),order.getId(),"AFTERSALE_DECIDED",
                        Math.addExact(order.getAuthVersion(),1),JsonUtils.toJsonString(detail),instant)!=1)
                    throw new IllegalStateException("原退款重试待发送事件保存失败");
                freshExecution(actor,checkedAt);return true;
            }));
        } catch (DuplicateKeyException conflict) {
            AfterSaleLogDO previous=decisions.receipt(actor.getTenantId(),body.getRequestId());
            if (previous==null) throw exception0(409,"原重试请求号已占用，请查询原退款");
            sameRetry(previous,actor,body,reason);claimed=false;
        }
        // 审计提交后才调用支付；异常/进程中断保留原UUID，查询不自动重新提交。
        if (claimed) execution.advance(body.getId(),RefundAction.RETRY);
        sameActor(actor);return snapshot(actor,body.getId(),body.getRefundNo());
    }

    private void sameRetry(AfterSaleLogDO previous,LoginUser actor,AfterSaleRefundRetryReqVO body,String reason) {
        if (!"ADMIN_REFUND_RETRY".equals(previous.getAction()) || !"ADMIN".equals(previous.getActorType())
                || !Objects.equals(previous.getActorId(),actor.getId()) || !Objects.equals(previous.getAfterSaleId(),body.getId())
                || !Objects.equals(previous.getAfterSaleVersion(),Math.addExact(body.getExpectedVersion(),1))
                || !Objects.equals(previous.getReason(),reason)) throw exception0(409,"原重试请求号不能更换售后、版本、操作者或原因");
    }

    /** 只读原操作者UUID审计回执；没有查到不证明另一个在途请求未提交。 */
    public AfterSaleRefundRetryResultRespVO retryResult(String requestId) {
        if (requestId==null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw exception0(400,"原重试请求号不正确");
        LoginUser actor=current();
        return read.execute(transaction -> {
            AfterSaleLogDO receipt=decisions.receipt(actor.getTenantId(),requestId);
            if (receipt==null) return null;
            if (!"ADMIN_REFUND_RETRY".equals(receipt.getAction()) || !"ADMIN".equals(receipt.getActorType())
                    || !Objects.equals(receipt.getActorId(),actor.getId())) throw exception0(404,"本人原重试回执不存在");
            AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),receipt.getAfterSaleId());
            if (plan==null) throw exception0(503,"原重试对应退款计划缺失");
            project(actor,plan);
            return new AfterSaleRefundRetryResultRespVO(plan.getAfterSaleId(),plan.getRefundNo(),requestId,receipt.getAfterSaleVersion());
        });
    }

    /** 只读原支付账预算；内部result不查询渠道、不提交或增授权。 */
    public AfterSaleRefundBudgetRespVO budget(Long id,String refundNo) {
        LoginUser actor=current();result(id,refundNo);
        PayRefundResultDTO payment=resources.refundPayment(id,RefundAction.READ);
        if (payment==null)throw exception0(404,"原支付退款尚未登记");
        sameActor(actor);return budgetResult(id,payment);
    }

    /** 保存当前财务人工依据后，只增加原号一次机会；不自动调用RETRY。 */
    public AfterSaleRefundBudgetRespVO continueRefund(AfterSaleRefundContinuationReqVO body) {
        if(body==null || !validator.validate(body).isEmpty() || body.getReason().trim().isEmpty()
                || body.getReason().chars().anyMatch(Character::isISOControl))throw exception0(400,"请填写有效的原退款人工继续依据");
        long checkedAt=System.nanoTime();LoginUser actor=current();
        AfterSaleRefundRespVO reference=snapshot(actor,body.getId(),body.getRefundNo());String reason=body.getReason().trim();
        try {
            write.executeWithoutResult(transaction -> {
                freshExecution(actor,checkedAt);
                TradeOrderDO order=fulfillment.lock(actor.getTenantId(),reference.getOrderId());
                AfterSaleDO sale=decisions.lock(actor.getTenantId(),body.getId());
                AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),body.getId());
                if(order==null || sale==null || plan==null || !Objects.equals(plan.getRefundNo(),body.getRefundNo())
                        || !Objects.equals(plan.getOrderId(),order.getId()) || !Objects.equals(sale.getOrderId(),order.getId())
                        || !Objects.equals(plan.getUserId(),order.getUserId()) || !Objects.equals(sale.getUserId(),order.getUserId())
                        || !Objects.equals(plan.getOrderItemId(),sale.getOrderItemId()))throw exception0(503,"原人工退款归属需核对");
                AfterSaleLogDO old=decisions.receipt(actor.getTenantId(),body.getRequestId());
                if(old!=null){sameContinuation(old,actor,body,reason);return;}
                if(sale.getStatus()!=45 || plan.getExecutionRequestedAt()==null || plan.getPayRefundId()==null
                        || !Objects.equals(sale.getAuthVersion(),body.getExpectedVersion())
                        || !Arrays.asList("PROCESSING","UNKNOWN","FAILED").contains(plan.getStatus())
                        || !Boolean.TRUE.equals(order.getPayStatus()) || !Arrays.asList(10,20,30).contains(order.getStatus()))
                    throw exception0(409,"请先同步原退款并核对当前版本");
                LocalDateTime instant=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
                if(refunds.executionCase(actor.getTenantId(),sale.getId(),sale.getAuthVersion(),instant)!=1
                        || refunds.executionOrder(actor.getTenantId(),order.getId(),order.getAuthVersion(),instant)!=1)
                    throw exception0(409,"原人工退款版本已变化");
                Map<String,Object> details=new LinkedHashMap<>();details.put("expectedPayVersion",body.getExpectedPayVersion());
                details.put("refundNo",plan.getRefundNo());details.put("afterSaleId",sale.getId());
                String json=JsonUtils.toJsonString(details);
                if(decisions.log(actor.getTenantId(),sale.getId(),actor.getId(),"ADMIN","ADMIN_REFUND_CONTINUE",45,45,
                        Math.addExact(sale.getAuthVersion(),1),instant,body.getRequestId(),reason,false,json)!=1
                        || fulfillment.log(actor.getTenantId(),order.getId(),actor.getId(),"ADMIN","AFTERSALE_REFUND_CONTINUE",
                        order.getStatus(),order.getStatus(),Math.addExact(order.getAuthVersion(),1),json,instant)!=1)
                    throw new IllegalStateException("原人工退款依据保存失败");
                details.put("orderId",order.getId());details.put("status",45);details.put("continuationRequested",true);
                String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();details.put("traceId",trace);
                if(fulfillment.event(UUID.randomUUID().toString(),actor.getTenantId(),order.getId(),"AFTERSALE_DECIDED",
                        Math.addExact(order.getAuthVersion(),1),JsonUtils.toJsonString(details),instant)!=1)
                    throw new IllegalStateException("原人工退款待发送事件保存失败");
                freshExecution(actor,checkedAt);
            });
        } catch(DuplicateKeyException conflict) {
            AfterSaleLogDO old=decisions.receipt(actor.getTenantId(),body.getRequestId());
            if(old==null)throw exception0(409,"原人工请求号已占用，请查询原退款");
            sameContinuation(old,actor,body,reason);
        }
        // 允许相同UUID恢复尚未到达pay的授权，pay侧同UUID只增一次；这里绝不提交退款。
        current();sameActor(actor);
        PayRefundResultDTO payment=resources.authorizeRefundContinuation(body.getId(),actor.getId(),body.getRequestId(),reason,body.getExpectedPayVersion());
        sameActor(actor);return budgetResult(body.getId(),payment);
    }

    private void sameContinuation(AfterSaleLogDO old,LoginUser actor,AfterSaleRefundContinuationReqVO body,String reason) {
        Map<?,?> details=old.getDetails()==null?null:JsonUtils.parseObject(old.getDetails(),Map.class);
        if(!"ADMIN_REFUND_CONTINUE".equals(old.getAction()) || !"ADMIN".equals(old.getActorType())
                || !Objects.equals(old.getActorId(),actor.getId()) || !Objects.equals(old.getAfterSaleId(),body.getId())
                || !Objects.equals(old.getAfterSaleVersion(),Math.addExact(body.getExpectedVersion(),1)) || !Objects.equals(old.getReason(),reason)
                || details==null || !Objects.equals(String.valueOf(details.get("expectedPayVersion")),body.getExpectedPayVersion().toString())
                || !Objects.equals(details.get("refundNo"),body.getRefundNo()))throw exception0(409,"原人工请求号不能更换退款、身份、版本或原因");
    }

    /** 先读原交易申请，再在事务外读原支付授权；不从当前次数增长推断本UUID成功。 */
    public AfterSaleRefundContinuationResultRespVO continuationResult(String requestId) {
        if(requestId==null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw exception0(400,"原人工请求号不正确");
        LoginUser actor=current();
        AfterSaleRefundContinuationResultRespVO reference=read.execute(transaction -> {
            AfterSaleLogDO receipt=decisions.receipt(actor.getTenantId(),requestId);
            if(receipt==null)return null;
            if(!"ADMIN_REFUND_CONTINUE".equals(receipt.getAction()) || !"ADMIN".equals(receipt.getActorType())
                    || !Objects.equals(receipt.getActorId(),actor.getId()))throw exception0(404,"本人原人工申请回执不存在");
            AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),receipt.getAfterSaleId());
            if(plan==null)throw exception0(503,"原人工退款计划缺失");
            project(actor,plan);
            Map<?,?> details=receipt.getDetails()==null?null:JsonUtils.parseObject(receipt.getDetails(),Map.class);
            Object value=details==null?null:details.get("expectedPayVersion");
            if(!(value instanceof Number) || !value.toString().matches("[0-9]{1,16}")
                    || !Objects.equals(details.get("refundNo"),plan.getRefundNo()))throw exception0(503,"原人工支付版本依据不完整");
            Long payVersion=Long.valueOf(value.toString());
            if(payVersion>9_007_199_254_740_990L)throw exception0(503,"原人工支付版本超出范围");
            return new AfterSaleRefundContinuationResultRespVO(plan.getAfterSaleId(),plan.getRefundNo(),requestId,
                    receipt.getAfterSaleVersion(),payVersion,false,null);
        });
        if(reference==null)return null;
        PayRefundContinuationResultDTO payment=resources.refundContinuationResult(reference.getId(),actor.getId(),requestId,reference.getExpectedPayVersion());
        sameActor(actor);
        return new AfterSaleRefundContinuationResultRespVO(reference.getId(),reference.getRefundNo(),requestId,reference.getRecordedVersion(),
                reference.getExpectedPayVersion(),payment!=null,payment==null?null:payment.getToLimit());
    }

    private AfterSaleRefundBudgetRespVO budgetResult(Long id,PayRefundResultDTO payment) {
        return new AfterSaleRefundBudgetRespVO(id,payment.getNo(),payment.getAuthVersion(),payment.getSubmitCount(),
                payment.getSubmissionLimit(),payment.getStatus(),epoch(payment.getRetryAfter()));
    }

    private AfterSaleRefundRespVO advance(AfterSaleRefundCommandReqVO body,RefundAction action) {
        if (body==null || !validator.validate(body).isEmpty()) throw exception0(400,"请提供原售后号和原退款号");
        long checkedAt=System.nanoTime();LoginUser actor=current();
        AfterSaleRefundRespVO reference=snapshot(actor,body.getId(),body.getRefundNo());
        if (action==RefundAction.SUBMIT) requestExecution(actor,reference,checkedAt);
        execution.advance(body.getId(),action);
        sameActor(actor);
        return snapshot(actor,body.getId(),body.getRefundNo());
    }

    /** 财务二次确认先提交再RPC；后台只发现这些原计划，重复原号不改最初执行身份。 */
    private void requestExecution(LoginUser actor,AfterSaleRefundRespVO reference,long checkedAt) {
        write.executeWithoutResult(transaction -> {
            freshExecution(actor,checkedAt);
            TradeOrderDO order=fulfillment.lock(actor.getTenantId(),reference.getOrderId());
            AfterSaleDO sale=decisions.lock(actor.getTenantId(),reference.getId());
            AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),reference.getId());
            if (order==null || sale==null || plan==null || !Objects.equals(plan.getRefundNo(),reference.getRefundNo())
                    || !Objects.equals(plan.getOrderId(),order.getId()) || !Objects.equals(sale.getOrderId(),order.getId())
                    || !Objects.equals(plan.getUserId(),order.getUserId()) || !Objects.equals(sale.getUserId(),order.getUserId())
                    || !Objects.equals(plan.getOrderItemId(),sale.getOrderItemId())) throw exception0(503,"原退款执行归属需核对");
            if (plan.getExecutionRequestedAt()!=null) {freshExecution(actor,checkedAt);return;}
            if (sale.getStatus()!=45 || !"PREPARED".equals(plan.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus())
                    || !Arrays.asList(10,20,30).contains(order.getStatus())) throw exception0(409,"原退款阶段不允许首次确认执行");
            LocalDateTime instant=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
            if (refunds.requestExecution(actor.getTenantId(),plan.getAfterSaleId(),actor.getId(),instant)!=1
                    || refunds.executionCase(actor.getTenantId(),sale.getId(),sale.getAuthVersion(),instant)!=1
                    || refunds.executionOrder(actor.getTenantId(),order.getId(),order.getAuthVersion(),instant)!=1)
                throw exception0(409,"原退款执行确认版本已变化");
            Map<String,Object> detail=new LinkedHashMap<>();detail.put("afterSaleId",sale.getId());
            detail.put("refundNo",plan.getRefundNo());detail.put("totalPrice",plan.getTotalPrice());
            long caseVersion=Math.addExact(sale.getAuthVersion(),1),orderVersion=Math.addExact(order.getAuthVersion(),1);
            if (decisions.log(actor.getTenantId(),sale.getId(),actor.getId(),"ADMIN","ADMIN_REFUND_EXECUTE",45,45,caseVersion,
                    instant,null,"确认执行原退款",false,JsonUtils.toJsonString(detail))!=1
                    || fulfillment.log(actor.getTenantId(),order.getId(),actor.getId(),"ADMIN","AFTERSALE_REFUND_EXECUTE",
                    order.getStatus(),order.getStatus(),orderVersion,JsonUtils.toJsonString(detail),instant)!=1)
                throw new IllegalStateException("原退款执行确认日志保存失败");
            detail.put("orderId",order.getId());detail.put("status",45);detail.put("executionRequested",true);
            String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();detail.put("traceId",trace);
            if (fulfillment.event(UUID.randomUUID().toString(),actor.getTenantId(),order.getId(),"AFTERSALE_DECIDED",orderVersion,
                    JsonUtils.toJsonString(detail),instant)!=1)throw new IllegalStateException("原退款执行待发送事件保存失败");
            freshExecution(actor,checkedAt);
        });
    }

    private void freshExecution(LoginUser actor,long checkedAt) {
        sameActor(actor);
        if (System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"退款执行权限核对已过时，请查询原退款");
    }

    /** 纯本地GET。财务可按原售后找回原号；指定原号时须相等，未找到不证明没有在途请求。 */
    public AfterSaleRefundRespVO result(Long id,String refundNo) {
        if (id==null || id<=0 || id>9_007_199_254_740_991L || refundNo!=null && !refundNo.matches("RF[0-9a-f]{32}"))
            throw exception0(400,"原售后号或退款号不正确");
        return snapshot(current(),id,refundNo);
    }

    /** 准备POST超时后，按原操作者与原UUID查询；不重新准备、不触发支付或恢复。 */
    public AfterSaleRefundRespVO preparationResult(String requestId) {
        if (requestId==null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw exception0(400,"原准备请求号不正确");
        LoginUser actor=current();
        return read.execute(transaction -> {
            AfterSaleRefundDO plan=refunds.request(actor.getTenantId(),requestId);
            if (plan==null) return null;
            if (!Objects.equals(plan.getActorId(),actor.getId())) throw exception0(404,"本人原准备记录不存在");
            return project(actor,plan);
        });
    }

    private AfterSaleRefundRespVO snapshot(LoginUser actor,Long id,String refundNo) {
        return read.execute(transaction -> {
            AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),id);
            if (plan==null || refundNo!=null && !Objects.equals(plan.getRefundNo(),refundNo)) throw exception0(404,"本租户原退款计划不存在");
            return project(actor,plan);
        });
    }

    private AfterSaleRefundRespVO project(LoginUser actor,AfterSaleRefundDO plan) {
        AfterSaleDO sale=decisions.get(actor.getTenantId(),plan.getAfterSaleId());
        TradeOrderDO order=orders.owned(actor.getTenantId(),plan.getUserId(),plan.getOrderId());
        if (sale==null || order==null || !Objects.equals(plan.getTenantId(),actor.getTenantId())
                || !Objects.equals(sale.getOrderId(),plan.getOrderId()) || !Objects.equals(sale.getUserId(),plan.getUserId())
                || !Objects.equals(sale.getOrderItemId(),plan.getOrderItemId()) || !Boolean.TRUE.equals(order.getPayStatus())
                || !Arrays.asList(45,50).contains(sale.getStatus())
                || "SUCCESS".equals(plan.getStatus()) != (sale.getStatus()==50)
                || !Objects.equals(plan.getRefundTime(),sale.getRefundTime())) throw exception0(503,"原退款进度与成交记录需核对");
        AfterSaleRefundRespVO value=new AfterSaleRefundRespVO();
        value.setId(plan.getAfterSaleId());value.setOrderId(plan.getOrderId());value.setRefundNo(plan.getRefundNo());
        value.setRequestId(plan.getRequestId());value.setPreparedVersion(plan.getAfterSaleVersion());value.setAuthVersion(sale.getAuthVersion());
        value.setStatus(plan.getStatus());value.setGoodsPrice(plan.getGoodsPrice());value.setDeliveryPrice(plan.getDeliveryPrice());value.setTotalPrice(plan.getTotalPrice());
        value.setRestoreCount(plan.getRestoreCount());value.setStockRecovered(plan.getStockRecovered());
        value.setCouponReturnRequired(plan.getCouponReturnRequired());value.setCouponRecovered(plan.getCouponRecovered());
        value.setConfirmedAt(epoch(plan.getRefundTime()));value.setUpdatedAt(epoch(plan.getUpdateTime()));
        sameActor(actor);return value;
    }

    private LoginUser current() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("财务入口不能放在外层事务中");
        LoginUser actor=identity();
        if (!security.hasPermission("trade:after-sale:refund")) throw exception0(403,"没有资金退款执行权限");
        return actor;
    }

    private LoginUser identity() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now())) throw exception0(401,"请重新登录");
        if (!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L
                || actor.getTenantId()==null || actor.getTenantId()<=0 || actor.getTenantId()>9_007_199_254_740_991L
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))
            throw exception0(403,"仅当前商家财务可操作原退款");
        return actor;
    }

    private void sameActor(LoginUser expected) {
        LoginUser actor=identity();
        if (!Objects.equals(actor.getId(),expected.getId()) || !Objects.equals(actor.getTenantId(),expected.getTenantId()))
            throw exception0(503,"财务身份已变化，请查询原请求");
    }
    private Long epoch(LocalDateTime value) {return value==null?null:value.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
}
