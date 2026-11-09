package cn.qinghe.mall.module.pay.framework.pay.core.client;

import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;

import java.util.Map;

/**
 * 支付客户端，用于对接各支付渠道的 SDK，实现发起支付、退款等功能
 *
 * @author 芋道源码
 */
public interface PayClient<Config> {

    /**
     * 获得渠道编号
     *
     * @return 渠道编号
     */
    Long getId();

    /**
     * 获得渠道配置
     *
     * @return 渠道配置
     */
    Config getConfig();

    // ============ 支付相关 ==========

    /**
     * 调用支付渠道，统一下单
     *
     * @param reqDTO 下单信息
     * @return 支付订单信息
     */
    PayOrderRespDTO unifiedOrder(PayOrderUnifiedReqDTO reqDTO);

    /** 显式恢复原扫码尝试：先查原号，仅同一渠道/原号/金额/截止重取，不代表安全关单。 */
    PayOrderRespDTO recoverQr(PayOrderUnifiedReqDTO reqDTO);

    /**
     * 解析 order 回调数据
     *
     * @param params HTTP 回调接口 content type 为 application/x-www-form-urlencoded 的所有参数
     * @param body HTTP 回调接口的 request body
     * @param headers HTTP 回调接口的 request headers
     * @return 支付订单信息
     */
    PayOrderRespDTO parseOrderNotify(Map<String, String> params, String body, Map<String, String> headers);

    /**
     * 获得支付订单信息
     *
     * @param outTradeNo 外部订单号
     * @return 支付订单信息
     */
    PayOrderRespDTO getOrder(String outTradeNo);

    // ============ 退款相关 ==========

    /**
     * 调用支付渠道，进行退款
     *
     * @param reqDTO  统一退款请求信息
     * @return 退款信息
     */
    PayRefundRespDTO unifiedRefund(PayRefundUnifiedReqDTO reqDTO);

    /**
     * 解析 refund 回调数据
     *
     * @param params HTTP 回调接口 content type 为 application/x-www-form-urlencoded 的所有参数
     * @param body HTTP 回调接口的 request body
     * @param headers HTTP 回调接口的 request headers
     * @return 支付订单信息
     */
    PayRefundRespDTO parseRefundNotify(Map<String, String> params, String body, Map<String, String> headers);

    /**
     * 获得退款订单信息
     *
     * @param outTradeNo 外部订单号
     * @param outRefundNo 外部退款号
     * @return 退款订单信息
     */
    PayRefundRespDTO getRefund(String outTradeNo, String outRefundNo);

    /** Confirm channel closure; unknown results must throw, not fabricate CLOSED. */
    PayOrderRespDTO closeOrder(String outTradeNo);
}
