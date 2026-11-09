package cn.qinghe.mall.module.pay.service.order;

import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderGetReqDTO;
import cn.qinghe.mall.module.pay.api.order.dto.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原expireOrder先检查渠道付款事实、再关闭的职责。
 * 本项目补同业务号的关闭先到记录；关闭意图提交后才执行渠道调用。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class PayOrderCloseService {
    private final PayOrderServiceImpl orders;
    private final PayOrderAttemptService attempts;

    /** 与关闭共用“先读取原账、再事务外查原尝试”的同步职责，供恢复任务补漏通知。 */
    public PayOrderRespDTO synchronizeOrder(PayOrderGetReqDTO request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("渠道查单必须在外层事务之外");
        PayOrderRespDTO result=orders.getOrder(request);
        if(result==null || !PayOrderStatusEnum.isWaiting(result.getStatus()) || Boolean.TRUE.equals(result.getReviewRequired()))return result;
        attempts.query(result.getId(),request.getUserId());
        return orders.getOrder(request);
    }

    public PayOrderRespDTO closeOrder(PayOrderCreateReqDTO request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("支付关闭编排不能放在外层事务内");
        PayOrderRespDTO result=orders.beginCloseOrder(request);
        rejectReview(result);
        if(!PayOrderStatusEnum.isWaiting(result.getStatus()))return result;

        // 原尝试可能已付而回调未到：查原号并复用同一入账方法，不能把未收到回调当成未付。
        attempts.query(result.getId(),request.getUserId());
        PayOrderGetReqDTO query=BeanUtils.toBean(request,PayOrderGetReqDTO.class);
        result=orders.getOrder(query);
        rejectReview(result);
        if(!PayOrderStatusEnum.isWaiting(result.getStatus()))return result;

        attempts.close(result.getId(),request.getUserId());
        result=orders.getOrder(query);
        rejectReview(result);
        return result;
    }

    private void rejectReview(PayOrderRespDTO result) {
        if(result==null)throw exception0(503,"原支付关闭结果待确认");
        if(Boolean.TRUE.equals(result.getReviewRequired()))throw exception0(409,"原支付存在异常付款，不能按未付关闭");
    }
}
