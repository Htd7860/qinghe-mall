package cn.qinghe.mall.module.pay.framework.pay.core.client.exception;
/** 渠道结果未知或无法核对；不能据此认定支付关闭。不给上层携带可能含敏感原报文的SDK异常。
 * 原PayClientException包装职责适配。
 */
public class PayClientException extends RuntimeException {
    public PayClientException(){super("支付渠道结果待确认，请查询原业务号");}
}
