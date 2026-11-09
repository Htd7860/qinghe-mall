package cn.qinghe.mall.module.pay.service.refund;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.refund.dto.*;
import cn.qinghe.mall.module.pay.dal.dataobject.app.PayAppDO;
import cn.qinghe.mall.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.qinghe.mall.module.pay.dal.dataobject.order.*;
import cn.qinghe.mall.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.qinghe.mall.module.pay.dal.dataobject.refund.PayRefundContinuationDO;
import cn.qinghe.mall.module.pay.dal.mysql.app.PayAppMapper;
import cn.qinghe.mall.module.pay.dal.mysql.channel.PayChannelMapper;
import cn.qinghe.mall.module.pay.dal.mysql.order.*;
import cn.qinghe.mall.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.qinghe.mall.module.pay.dal.mysql.refund.PayRefundContinuationMapper;
import cn.qinghe.mall.module.pay.enums.refund.PayRefundStatusEnum;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClient;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import cn.qinghe.mall.module.pay.service.channel.PayChannelService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原createRefund/notifyRefundSuccess/syncRefund职责，登记与渠道调用分离。
 * 仅供可信trade服务：必须从不可撤销原售后计划构造命令，不对浏览器开放。
 * 本地事务只维护退款/支付账；不在锁内RPC，不直接修改交易、库存、券或伪造业务通知。
 * @author 芋道源码
 */
@Service
public class PayRefundService {
    private final PayOrderMapper orders;
    private final PayOrderExtensionMapper attempts;
    private final PayRefundMapper refunds;
    private final PayRefundContinuationMapper continuations;
    private final PayAppMapper apps;
    private final PayChannelMapper channels;
    private final PayChannelService clients;
    private final Validator validator;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public PayRefundService(PayOrderMapper orders, PayOrderExtensionMapper attempts, PayRefundMapper refunds,
            PayAppMapper apps, PayChannelMapper channels, PayChannelService clients, Validator validator,
            PlatformTransactionManager manager, PayRefundContinuationMapper continuations) {
        this.orders = orders;
        this.attempts = attempts;
        this.refunds = refunds;
        this.continuations=continuations;
        this.apps = apps;
        this.channels = channels;
        this.clients = clients;
        this.validator = validator;
        write = new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
        read = new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);
        read.setTimeout(5);
    }

    /** 只登记和占用额度；重复同业务号返回原记录，不自动再次调用渠道。 */
    public PayRefundResultDTO createRefund(PayRefundCreateReqDTO request) {
        Long tenant = context(request);
        if (request.getRefundPrice() > request.getPayPrice()
                || request.getReason().trim().isEmpty()
                || request.getReason().chars().anyMatch(Character::isISOControl)) {
            throw exception0(400, "原退款金额或原因不正确");
        }
        try {
            return write.execute(transaction -> {
                PayOrderDO order = order(tenant, request, true);
                PayRefundDO previous = refunds.find(tenant, order.getAppId(), request.getMerchantRefundId());
                if (previous != null) {
                    same(previous, request);
                    return result(previous);
                }
                paid(order);
                balance(order);
                if (refunds.pending(tenant, order.getId()) != 0
                        || Math.addExact(order.getRefundPrice(), request.getRefundPrice()) > order.getPrice()) {
                    throw exception0(409, "原支付有退款待确认或余额不足，请查询原退款号");
                }
                if (!Objects.equals(order.getPrice(), request.getPayPrice())) {
                    throw exception0(409, "原支付金额与交易退款计划不一致");
                }
                PayOrderExtensionDO attempt = attempts.find(tenant, order.getId());
                if (attempt == null || !"SUCCESS".equals(attempt.getState()) || attempt.getChannelOrderNo() == null
                        || !Objects.equals(attempt.getSuccessTime(), order.getSuccessTime())) {
                    throw exception0(409, "原支付渠道未确认正常到账，不能退款");
                }
                PayChannelDO channel = channels.get(tenant, attempt.getChannelId());
                if (channel == null || !Objects.equals(channel.getAppId(), order.getAppId())
                        || !Objects.equals(channel.getAuthVersion(), attempt.getChannelVersion())
                        || !"alipay_qr".equals(channel.getCode())) {
                    throw exception0(503, "原支付渠道身份或版本需核对");
                }
                LocalDateTime instant = now();
                PayRefundDO row = BeanUtils.toBean(request, PayRefundDO.class);
                row.setTenantId(tenant);
                row.setAppId(order.getAppId());
                row.setChannelId(attempt.getChannelId());
                row.setChannelVersion(attempt.getChannelVersion());
                row.setOrderNo(attempt.getNo());
                row.setChannelOrderNo(attempt.getChannelOrderNo());
                row.setUserType(UserTypeEnum.MEMBER.getValue());
                row.setReason(request.getReason().trim());
                row.setStatus("PREPARED");
                row.setSubmitCount(0);
                row.setSubmissionLimit(3);
                row.setAuthVersion(0L);
                row.setCreateTime(instant);
                row.setUpdateTime(instant);
                changed(refunds.insert(row));
                if (row.getId() == null || row.getId() <= 0 || row.getId() > 9_007_199_254_740_991L) {
                    throw exception0(503, "原退款编号无法确认");
                }
                changed(orders.reserveRefund(tenant, order.getId(), order.getAuthVersion(), row.getRefundPrice(), instant));
                return result(row);
            });
        } catch (DuplicateKeyException conflict) {
            return read.execute(transaction -> {
                PayOrderDO order = order(tenant, request, false);
                PayRefundDO original = refunds.find(tenant, order.getAppId(), request.getMerchantRefundId());
                if (original == null) throw exception0(409, "原退款号已占用，请查询原业务退款");
                same(original, request);
                return result(original);
            });
        }
    }

    /** 纯账本读取；没有查到不代表另一个请求未提交，不触发渠道查询或新退款。 */
    public PayRefundResultDTO getRefund(PayRefundGetReqDTO request) {
        Long tenant = context(request);
        return read.execute(transaction -> {
            PayOrderDO order = order(tenant, request, false);
            PayRefundDO row = refunds.find(tenant, order.getAppId(), request.getMerchantRefundId());
            if (row == null) return null;
            same(row, request);
            return result(row);
        });
    }

    public PayRefundResultDTO submitRefund(PayRefundGetReqDTO request) {
        return submit(request, false);
    }

    /** 显式原号重试先查原结果，次数/间隔受原账限制；不生成新退款号或重新占用金额。 */
    public PayRefundResultDTO retryRefund(PayRefundGetReqDTO request) {
        PayRefundResultDTO previous = synchronizeRefund(request);
        if ("SUCCESS".equals(previous.getStatus())) return previous;
        return submit(request, true);
    }

    /** 仅增加一次原号机会，不调用退款提交；trade须先验证当前财务权限并保存原人工依据。 */
    public PayRefundResultDTO authorizeContinuation(PayRefundContinuationReqDTO request) {
        Long tenant=context(request);
        if (request.getReason().trim().isEmpty() || request.getReason().chars().anyMatch(Character::isISOControl))
            throw exception0(400,"请填写原退款人工继续原因");
        PayRefundDO reference=existing(tenant,request);
        PayRefundContinuationDO old=continuations.get(tenant,request.getRequestId());
        if (old!=null) {sameContinuation(old,reference,request);return getRefund(request);}
        PayRefundResultDTO queried=synchronizeRefund(request);
        if ("SUCCESS".equals(queried.getStatus())) return queried;
        try {
            return write.execute(transaction -> {
                PayOrderDO order=order(tenant,request,true);PayRefundDO current=existing(tenant,request);
                PayRefundContinuationDO prior=continuations.get(tenant,request.getRequestId());
                if (prior!=null) {sameContinuation(prior,current,request);return result(current);}
                if ("SUCCESS".equals(current.getStatus())) return result(current);
                paid(order);balance(order);submissionBudget(current);
                if (!Objects.equals(current.getAuthVersion(),request.getExpectedVersion())
                        || current.getSubmissionLimit()==null || !Objects.equals(current.getSubmitCount(),current.getSubmissionLimit())
                        || current.getSubmissionLimit()==Integer.MAX_VALUE || "PREPARED".equals(current.getStatus()))
                    throw exception0(409,"原退款预算或版本已变化，请查询后重新核对");
                LocalDateTime instant=now();PayRefundContinuationDO receipt=new PayRefundContinuationDO();
                receipt.setTenantId(tenant);receipt.setRefundId(current.getId());receipt.setRequestId(request.getRequestId());
                receipt.setActorId(request.getActorId());receipt.setReason(request.getReason().trim());receipt.setFromVersion(current.getAuthVersion());
                receipt.setFromLimit(current.getSubmissionLimit());receipt.setToLimit(Math.addExact(current.getSubmissionLimit(),1));receipt.setCreateTime(instant);
                changed(continuations.insert(receipt));changed(refunds.authorizeContinuation(tenant,current.getId(),current.getAuthVersion(),instant));
                current.setSubmissionLimit(receipt.getToLimit());current.setAuthVersion(Math.addExact(current.getAuthVersion(),1));current.setUpdateTime(instant);
                return result(current);
            });
        } catch (DuplicateKeyException conflict) {
            PayRefundContinuationDO receipt=continuations.get(tenant,request.getRequestId());
            if (receipt==null) throw exception0(409,"原人工继续依据已变化，请查询原记录");
            sameContinuation(receipt,reference,request);return getRefund(request);
        }
    }

    /** 纯账本读取；查不到不能据此自动换UUID授权。 */
    public PayRefundContinuationResultDTO continuationResult(PayRefundContinuationGetReqDTO request) {
        Long tenant=context(request);
        return read.execute(transaction -> {
            PayRefundDO refund=existing(tenant,request);
            PayRefundContinuationDO receipt=continuations.get(tenant,request.getRequestId());
            if(receipt==null)return null;
            if(!Objects.equals(receipt.getRefundId(),refund.getId()) || !Objects.equals(receipt.getActorId(),request.getActorId()))
                throw exception0(404,"原操作者的人工授权回执不存在");
            submissionBudget(refund);
            return new PayRefundContinuationResultDTO(refund.getId(),refund.getNo(),receipt.getRequestId(),
                    receipt.getFromVersion(),receipt.getFromLimit(),receipt.getToLimit());
        });
    }

    private void sameContinuation(PayRefundContinuationDO receipt,PayRefundDO refund,PayRefundContinuationReqDTO request) {
        if (!Objects.equals(receipt.getRefundId(),refund.getId()) || !Objects.equals(receipt.getActorId(),request.getActorId())
                || !Objects.equals(receipt.getFromVersion(),request.getExpectedVersion()) || !Objects.equals(receipt.getReason(),request.getReason().trim()))
            throw exception0(409,"原人工请求号不能更换退款、操作者、版本或原因");
    }

    /** 原渠道查询，失败/未知不释放占用；已成功的账本不再依赖渠道可用。 */
    public PayRefundResultDTO synchronizeRefund(PayRefundGetReqDTO request) {
        Long tenant = context(request);
        PayRefundDO row = existing(tenant, request);
        if ("SUCCESS".equals(row.getStatus()) || "PREPARED".equals(row.getStatus())) return result(row);
        PayClient<AlipayPayClientConfig> client = client(row);
        PayRefundRespDTO response;
        try {
            response = client.getRefund(row.getOrderNo(), row.getNo());
        } catch (RuntimeException unknown) {
            return record(tenant, request, null);
        }
        return record(tenant, request, response);
    }

    private PayRefundResultDTO submit(PayRefundGetReqDTO request, boolean retry) {
        Long tenant = context(request);
        PayRefundDO original = existing(tenant, request);
        if ("SUCCESS".equals(original.getStatus()) || !retry && !"PREPARED".equals(original.getStatus())) {
            return result(original);
        }
        // 凭据/客户端在领取前核对；配置缺失不会伪造已经发出退款。
        PayChannelDO configured = channel(original);
        PayClient<AlipayPayClientConfig> client = clients.client(configured, original.getChannelVersion());
        PayRefundDO row = write.execute(transaction -> {
            PayOrderDO order = order(tenant, request, true);
            PayRefundDO current = existing(tenant, request);
            if ("SUCCESS".equals(current.getStatus()) || !retry && !"PREPARED".equals(current.getStatus())) return null;
            submissionBudget(current);
            paid(order);
            balance(order);
            PayChannelDO lockedChannel = channels.lock(tenant, current.getChannelId());
            if (!configured.equals(lockedChannel)) {
                throw exception0(409, "原退款渠道配置已变化，请核查后继续");
            }
            LocalDateTime instant = now();
            LocalDateTime lastSubmission = refunds.lastSubmission(tenant, order.getId());
            if (lastSubmission != null && instant.isBefore(lastSubmission.plusSeconds(3))) {
                throw exception0(409, "原支付的退款提交间隔不足，请稍后查询并继续");
            }
            if (current.getSubmitCount() >= current.getSubmissionLimit() || current.getLastSubmitTime() != null
                    && instant.isBefore(current.getLastSubmitTime().plusSeconds(30))) {
                throw exception0(409, "原退款仍需查询，重试间隔或次数未满足");
            }
            changed(refunds.submit(tenant, current.getId(), current.getAuthVersion(), retry, instant));
            return current;
        });
        if (row == null) return getRefund(request);
        PayRefundUnifiedReqDTO command = new PayRefundUnifiedReqDTO();
        command.setOutTradeNo(row.getOrderNo());
        command.setOutRefundNo(row.getNo());
        command.setPayPrice(row.getPayPrice());
        command.setRefundPrice(row.getRefundPrice());
        command.setReason(row.getReason());
        PayRefundRespDTO response;
        try {
            response = client.unifiedRefund(command);
        } catch (RuntimeException unknown) {
            return record(tenant, request, null);
        }
        return record(tenant, request, response);
    }

    /** 只有本类固定原号的渠道调用可进入；不提供接受外部SUCCESS声明的接口。 */
    private PayRefundResultDTO record(Long tenant, PayRefundGetReqDTO request, PayRefundRespDTO response) {
        return write.execute(transaction -> {
            PayOrderDO order = order(tenant, request, true);
            PayRefundDO row = existing(tenant, request);
            if (response != null && !Objects.equals(row.getNo(), response.getOutRefundNo())) {
                throw exception0(503, "渠道退款号与原计划不一致，请核对原结果");
            }
            if ("SUCCESS".equals(row.getStatus())) return result(row);
            if (row.getSubmittedTime() == null) throw exception0(409, "原退款尚未登记渠道提交");
            paid(order);
            balance(order);
            LocalDateTime instant = now();
            boolean success = response != null && PayRefundStatusEnum.isSuccess(response.getStatus());
            String state = success ? "SUCCESS"
                    : response != null && PayRefundStatusEnum.isFailure(response.getStatus()) ? "FAILED" : "UNKNOWN";
            String channelNo = response == null ? null : response.getChannelRefundNo();
            if (channelNo != null && !channelNo.matches("[A-Za-z0-9_-]{1,128}")) {
                throw exception0(503, "渠道退款标识需核对");
            }
            if (row.getChannelRefundNo() != null && channelNo != null && !row.getChannelRefundNo().equals(channelNo)) {
                throw exception0(503, "渠道退款标识与原记录不一致");
            }
            LocalDateTime succeeded = success ? response.getSuccessTime() : null;
            if (success) {
                if (!Objects.equals(response.getRefundPrice(), row.getRefundPrice()) || succeeded == null
                        || succeeded.isBefore(order.getSuccessTime())
                        || succeeded.isBefore(row.getSubmittedTime().truncatedTo(ChronoUnit.SECONDS))
                        || succeeded.isAfter(instant.plusSeconds(1)) || succeeded.getNano() % 1000 != 0) {
                    throw exception0(503, "渠道退款金额或成功时间需核对");
                }
                changed(orders.refundSucceeded(tenant, order.getId(), order.getAuthVersion(), row.getRefundPrice(), instant));
            } else if ("FAILED".equals(row.getStatus()) || state.equals(row.getStatus())) {
                return result(row); // 未确认结果不能覆盖已确认成功，也不反复改写同一未知状态。
            }
            changed(refunds.record(tenant, row.getId(), row.getAuthVersion(), state, channelNo, succeeded, instant));
            row.setStatus(state);
            if (channelNo != null) row.setChannelRefundNo(channelNo);
            row.setSuccessTime(succeeded);
            row.setAuthVersion(Math.addExact(row.getAuthVersion(), 1));
            row.setUpdateTime(instant);
            return result(row);
        });
    }

    private void balance(PayOrderDO order) {
        long success = refunds.successPrice(order.getTenantId(), order.getId());
        long reserved = refunds.reservedPrice(order.getTenantId(), order.getId());
        if (order.getRefundPrice() == null || order.getRefundReserved() == null
                || success != order.getRefundPrice() || reserved != order.getRefundReserved()
                || Math.addExact(success, reserved) > order.getPrice()) {
            throw exception0(503, "原支付退款余额不一致，请先核查");
        }
    }

    private PayRefundDO existing(Long tenant, PayRefundGetReqDTO request) {
        PayRefundDO row = refunds.findNo(tenant, request.getNo());
        if (row == null) throw exception0(404, "原支付退款不存在");
        same(row, request);
        return row;
    }

    private void same(PayRefundDO row, PayRefundGetReqDTO request) {
        if (!Objects.equals(row.getOrderId(), request.getOrderId()) || !Objects.equals(row.getUserId(), request.getUserId())
                || !Objects.equals(row.getMerchantOrderId(), request.getMerchantOrderId())
                || !Objects.equals(row.getMerchantRefundId(), request.getMerchantRefundId()) || !Objects.equals(row.getNo(), request.getNo())) {
            throw exception0(409, "原退款业务归属或退款号不能改变");
        }
        if (request instanceof PayRefundCreateReqDTO) {
            PayRefundCreateReqDTO create = (PayRefundCreateReqDTO) request;
            if (!Objects.equals(row.getPayPrice(), create.getPayPrice()) || !Objects.equals(row.getRefundPrice(), create.getRefundPrice())
                    || !Objects.equals(row.getReason(), create.getReason().trim())) {
                throw exception0(409, "同一退款不能更换原支付金额、退款金额或原因");
            }
        }
    }

    private PayOrderDO order(Long tenant, PayRefundGetReqDTO request, boolean lock) {
        PayOrderDO row = lock ? orders.lockId(tenant, request.getOrderId()) : orders.get(tenant, request.getOrderId());
        PayAppDO app = apps.find(tenant, request.getAppKey());
        if (row == null || app == null || !Objects.equals(row.getAppId(), app.getId())
                || !Objects.equals(row.getUserId(), request.getUserId()) || !Objects.equals(row.getMerchantOrderId(), request.getMerchantOrderId())
                || !UserTypeEnum.MEMBER.getValue().equals(row.getUserType())) {
            throw exception0(404, "原本人支付单或应用不存在");
        }
        return row;
    }

    private void paid(PayOrderDO order) {
        if (!(Integer.valueOf(10).equals(order.getStatus()) || Integer.valueOf(20).equals(order.getStatus()))
                || order.getSuccessTime() == null || Boolean.TRUE.equals(order.getClosing())) {
            throw exception0(409, "原支付未确认正常到账或正在关闭核查");
        }
    }

    private PayClient<AlipayPayClientConfig> client(PayRefundDO row) {
        return clients.client(channel(row), row.getChannelVersion());
    }

    private PayChannelDO channel(PayRefundDO row) {
        PayChannelDO channel = channels.get(row.getTenantId(), row.getChannelId());
        if (channel == null || !Objects.equals(channel.getAppId(), row.getAppId())
                || !Objects.equals(channel.getAuthVersion(), row.getChannelVersion())) {
            throw exception0(503, "原退款渠道身份或版本不可用，不能切换其他渠道");
        }
        return channel;
    }

    private Long context(PayRefundGetReqDTO request) {
        if (request == null || !validator.validate(request).isEmpty()
                || Long.parseLong(request.getMerchantOrderId()) > 9_007_199_254_740_991L
                || Long.parseLong(request.getMerchantRefundId()) > 9_007_199_254_740_991L) {
            throw exception0(400, "原退款身份参数不正确");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("支付退款编排不能放在外层事务内");
        }
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || tenant > 9_007_199_254_740_991L || TenantContextHolder.isIgnore()) {
            throw exception0(403, "退款需要可信服务租户");
        }
        return tenant;
    }

    private void submissionBudget(PayRefundDO row) {
        if (row.getSubmissionLimit()==null || row.getSubmissionLimit().longValue()!=Math.addExact(3L,continuations.count(row.getTenantId(),row.getId())))
            throw exception0(503,"原退款提交预算与人工依据不一致");
    }

    private PayRefundResultDTO result(PayRefundDO row) {
        PayRefundResultDTO result = BeanUtils.toBean(row, PayRefundResultDTO.class);
        result.setAppKey("qinghe-trade");
        if (!"SUCCESS".equals(row.getStatus()) && row.getSubmitCount() < row.getSubmissionLimit() && row.getLastSubmitTime() != null) {
            result.setRetryAfter(row.getLastSubmitTime().plusSeconds(30));
        }
        return result;
    }

    private void changed(int count) {
        if (count != 1) throw exception0(409, "原退款账或版本已变化，请查询原号");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
    }
}
