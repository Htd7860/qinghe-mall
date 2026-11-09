package cn.qinghe.mall.module.pay.service.order;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.pay.api.order.dto.*;
import cn.qinghe.mall.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.qinghe.mall.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.qinghe.mall.module.pay.dal.mysql.channel.PayChannelMapper;
import cn.qinghe.mall.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原submitOrder选择渠道→原尝试→client职责；外层trade已核对本人/购买资格。
 * 固定一个支付宝扫码渠道，浏览器不能指定渠道/会员/金额；历史重试不得切渠道。
 * @author 芋道源码
 */
@Service @RequiredArgsConstructor
public class PayOrderSubmissionService {
    private final PayOrderServiceImpl orders;
    private final PayOrderAttemptService attempts;
    private final PayChannelMapper channels;
    private final PayOrderExtensionMapper extensions;

    public PayOrderSubmissionRespDTO recoverQr(PayOrderGetReqDTO request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("二维码恢复不能放在外层事务内");
        PayOrderRespDTO payment=orders.getOrder(request);
        if(payment==null)throw exception0(404,"本人支付单不存在");
        attempts.recoverQr(payment.getId(),request.getUserId());
        return orders.submissionResult(request);
    }

    public PayOrderSubmissionRespDTO submit(PayOrderGetReqDTO request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("渠道提交不能放在外层事务内");
        PayOrderRespDTO payment=orders.getOrder(request);
        if(payment==null)throw exception0(404,"本人支付单不存在");
        if(payment.getStatus()!=0 || Boolean.TRUE.equals(payment.getClosing()) || Boolean.TRUE.equals(payment.getReviewRequired()))
            return orders.submissionResult(request);
        PayOrderExtensionDO previous=extensions.find(TenantContextHolder.getTenantId(),payment.getId());
        Long channelId;
        if(previous!=null)channelId=previous.getChannelId();
        else {
            List<PayChannelDO> configured=channels.enabledQr(payment.getTenantId(),payment.getAppId());
            if(configured.size()!=1)throw exception0(503,"本店支付宝扫码渠道尚未配置或不唯一");
            channelId=configured.get(0).getId();
        }
        attempts.submit(payment.getId(),request.getUserId(),channelId);
        return orders.submissionResult(request);
    }
}
