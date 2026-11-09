package cn.qinghe.mall.module.pay.service.order;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.*;
import cn.qinghe.mall.module.pay.dal.dataobject.app.PayAppDO;
import cn.qinghe.mall.module.pay.dal.dataobject.order.PayOrderDO;
import cn.qinghe.mall.module.pay.dal.mysql.app.PayAppMapper;
import cn.qinghe.mall.module.pay.dal.mysql.order.PayOrderMapper;
import cn.qinghe.mall.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.qinghe.mall.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原createOrder的应用校验→原业务单查询→WAITING保存；补tenant/用户/金额/期限不可变。
 * 无第三方渠道调用，不把创建支付单当支付成功；支付应用配置为空时明确拒绝。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=10,rollbackFor=Exception.class)
public class PayOrderServiceImpl {
    private final PayAppMapper apps;
    private final PayOrderMapper orders;
    private final PayOrderExtensionMapper attempts;
    private final Validator validator;

    public PayOrderRespDTO createOrder(PayOrderCreateReqDTO request) {
        validate(request);
        Long tenant=tenant();merchantId(request.getMerchantOrderId());
        PayAppDO app=app(tenant,request.getAppKey());
        PayOrderDO previous=orders.find(tenant,app.getId(),request.getMerchantOrderId());
        if(previous!=null){same(previous,request);return result(previous,app);}
        if(!Integer.valueOf(0).equals(app.getStatus()))throw exception0(403,"本店支付应用已停用");
        if(!now().isBefore(request.getExpireTime()))throw exception0(409,"原订单付款时间已到期");

        PayOrderDO row=BeanUtils.toBean(request,PayOrderDO.class);
        row.setTenantId(tenant);row.setAppId(app.getId());row.setStatus(PayOrderStatusEnum.WAITING.getStatus());
        row.setRefundPrice(0L);row.setAuthVersion(0L);row.setCreateTime(now());row.setUpdateTime(row.getCreateTime());
        int inserted=orders.ensure(row);
        if(inserted!=0 && inserted!=1)throw new IllegalStateException("支付单登记结果异常");
        row=orders.lock(tenant,app.getId(),request.getMerchantOrderId());
        if(row==null)throw exception0(409,"支付应用状态已变化，请查询原订单");
        same(row,request);
        if(inserted==1 && !now().isBefore(row.getExpireTime()))throw exception0(409,"保存期间付款时间已到期");
        return result(row,app);
    }

    /** 先提交关闭意图，再由独立编排方法查渠道/关渠道；本方法不调用外部服务。 */
    public PayOrderRespDTO beginCloseOrder(PayOrderCreateReqDTO request) {
        validate(request);
        Long tenant=tenant();merchantId(request.getMerchantOrderId());
        PayAppDO app=app(tenant,request.getAppKey());
        PayOrderDO marker=BeanUtils.toBean(request,PayOrderDO.class);
        marker.setTenantId(tenant);marker.setAppId(app.getId());
        marker.setCreateTime(now());marker.setUpdateTime(marker.getCreateTime());
        int inserted=orders.ensureClosed(marker);
        if(inserted!=0 && inserted!=1)throw new IllegalStateException("支付关闭登记结果异常");
        PayOrderDO row=orders.lock(tenant,app.getId(),request.getMerchantOrderId());
        if(row==null)throw exception0(503,"原支付关闭记录待确认");
        same(row,request);
        if(row.getStatus()==0 && !Boolean.TRUE.equals(row.getClosing())) {
            if(orders.beginClose(tenant,row.getId(),row.getAuthVersion(),now())!=1)
                throw exception0(409,"原支付关闭版本已变化");
            row.setClosing(true);row.setAuthVersion(Math.addExact(row.getAuthVersion(),1));
        }
        return result(row,app);
    }

    /** 查询包括停用应用和已过期历史，不按当前时间伪造渠道关闭或支付失败。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5,readOnly=true)
    public PayOrderRespDTO getOrder(PayOrderGetReqDTO request) {
        if(request==null || !validator.validate(request).isEmpty())throw exception0(400,"支付单查询参数不正确");
        Long tenant=tenant();merchantId(request.getMerchantOrderId());
        PayAppDO app=app(tenant,request.getAppKey());
        PayOrderDO row=orders.find(tenant,app.getId(),request.getMerchantOrderId());
        if(row==null || !Objects.equals(row.getUserId(),request.getUserId()))return null;
        return result(row,app);
    }

    /** 与原getOrder展示职责一致；同一RR快照读取主账和原尝试，不查询渠道、不创建支付。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5,readOnly=true)
    public PayOrderSubmissionRespDTO submissionResult(PayOrderGetReqDTO request) {
        PayOrderRespDTO payment=getOrder(request);
        if(payment==null)return null;
        PayOrderExtensionDO attempt=attempts.find(payment.getTenantId(),payment.getId());
        PayOrderSubmissionRespDTO result=new PayOrderSubmissionRespDTO();result.setPayment(payment);
        String state=Boolean.TRUE.equals(payment.getReviewRequired())?"REVIEW"
                :Boolean.TRUE.equals(payment.getClosing()) && payment.getStatus()==0?"CLOSING"
                :payment.getStatus()==30?"CLOSED":payment.getStatus()==10 || payment.getStatus()==20?"SUCCESS"
                :attempt==null?"NOT_SUBMITTED":attempt.getState();
        result.setState(state);
        result.setQrRecoveryCount(attempt==null?0:attempt.getQrRecoveryCount());
        result.setQrRecoveryAfter(attempt==null?null:attempt.getQrRecoveryAfter()==null?attempt.getCreateTime().plusSeconds(30):attempt.getQrRecoveryAfter());
        if("WAITING".equals(state) && payment.getStatus()==0 && now().isBefore(payment.getExpireTime()))
            result.setQrCode(attempt.getDisplayContent()==null || attempt.getDisplayContent().isEmpty()?null:attempt.getDisplayContent());
        return result;
    }

    private void validate(PayOrderCreateReqDTO request) {
        if(request==null || !validator.validate(request).isEmpty()
                || !UserTypeEnum.MEMBER.getValue().equals(request.getUserType())
                || request.getSubject().chars().anyMatch(Character::isISOControl)
                || request.getExpireTime().getNano()%1000!=0)throw exception0(400,"支付单参数不正确");
    }
    private PayAppDO app(Long tenant,String key) {
        PayAppDO app=apps.find(tenant,key);
        if(app==null)throw exception0(503,"本店支付应用尚未配置");
        return app;
    }
    private void same(PayOrderDO row,PayOrderCreateReqDTO request) {
        if(!Objects.equals(row.getUserId(),request.getUserId()) || !Objects.equals(row.getUserType(),request.getUserType())
                || !Objects.equals(row.getPrice(),request.getPrice()) || !Objects.equals(row.getSubject(),request.getSubject())
                || !Objects.equals(row.getExpireTime(),request.getExpireTime()))throw exception0(409,"原支付单不能更换会员、金额、标题或付款期限");
    }
    private PayOrderRespDTO result(PayOrderDO row,PayAppDO app) {
        if(row.getId()==null || row.getId()<=0 || row.getId()>9_007_199_254_740_991L)throw exception0(503,"支付单编号无法投影");
        PayOrderRespDTO result=BeanUtils.toBean(row,PayOrderRespDTO.class);result.setAppKey(app.getAppKey());
        PayOrderExtensionDO attempt=attempts.find(row.getTenantId(),row.getId());
        result.setReviewRequired(attempt!=null && "REVIEW".equals(attempt.getState()));return result;
    }
    private Long tenant() {
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore())throw exception0(403,"支付操作需要可信租户");
        return tenant;
    }
    private void merchantId(String id){if(Long.parseLong(id)>9_007_199_254_740_991L)throw exception0(400,"原订单编号超出范围");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
