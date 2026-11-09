package cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.AbstractPayClient;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.internal.util.AntCertificationUtil;
import com.alipay.api.internal.util.codec.Base64;
import com.alipay.api.request.*;
import com.alipay.api.response.*;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;
import javax.validation.Validator;
import java.math.BigDecimal;
import java.time.ZoneId;
import cn.qinghe.mall.module.pay.framework.pay.core.client.exception.PayClientException;
import java.util.Map;
import java.util.Objects;

import static cn.hutool.core.date.DatePattern.NORM_DATETIME_FORMATTER;
import static cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig.MODE_CERTIFICATE;
import static cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig.MODE_PUBLIC_KEY;

/**
 * 支付宝抽象类，实现支付宝统一的接口、以及部分实现（退款）
 *
 * @author jason
 */
public abstract class AbstractAlipayPayClient extends AbstractPayClient<AlipayPayClientConfig> {

    protected DefaultAlipayClient client;

    public AbstractAlipayPayClient(Long channelId, AlipayPayClientConfig config, Validator validator) {
        super(channelId, config, validator);
    }

    @Override
    protected void doInit() throws AlipayApiException {
        AlipayConfig alipayConfig = new AlipayConfig();
        BeanUtil.copyProperties(config, alipayConfig, false);
        alipayConfig.setConnectTimeout(1000);
        alipayConfig.setReadTimeout(10000);
        alipayConfig.setCharset("UTF-8");
        alipayConfig.setFormat("json");
        this.client = new DefaultAlipayClient(alipayConfig);
    }

    // ============ 支付相关 ==========

    @Override
    protected PayOrderRespDTO doParseOrderNotify(Map<String,String> params,String body,Map<String,String> headers) throws Exception {
        verifyNotifyData(params);
        if (!Objects.equals(config.getAppId(),params.get("app_id"))
                || !Objects.equals(config.getSellerId(),params.get("seller_id"))
                || !"RSA2".equals(params.get("sign_type"))) throw new PayClientException();
        String out=params.get("out_trade_no");businessNo(out);
        Integer status=parseStatus(params.get("trade_status"));
        if(status==null)throw new PayClientException();
        if(PayOrderStatusEnum.isClosed(status))throw new PayClientException(); // 已付全退也可能TRADE_CLOSED，交给账本/退款查询收敛
        Long price=parseAmount(params.get("total_amount"));
        String currency=params.get("trans_currency");
        if(currency!=null && !"CNY".equals(currency))throw new PayClientException();
        LocalDateTime success=parseTime(params.get("gmt_payment"));
        String trade=params.get("trade_no");
        if(PayOrderStatusEnum.isSuccess(status) && (success==null || trade==null || trade.isEmpty()))throw new PayClientException();
        PayOrderRespDTO result=PayOrderRespDTO.of(status,trade,null,success,out,null);
        result.setPrice(price);return result;
    }

    @Override
    protected PayOrderRespDTO doGetOrder(String outTradeNo) throws Exception {
        return parseOrderQuery(outTradeNo,queryOrder(outTradeNo));
    }
    /** SDK签名/HTTP处理仍由原client执行；恢复分支只额外识别精确TRADE_NOT_EXIST。 */
    protected AlipayTradeQueryResponse queryOrder(String outTradeNo) throws AlipayApiException {
        // 1.1 构建 AlipayTradeRefundModel 请求
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        // 1.2 构建 AlipayTradeQueryRequest 请求
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        AlipayTradeQueryResponse response;
        if (Objects.equals(config.getMode(), MODE_CERTIFICATE)) {
            // 证书模式
            response = client.certificateExecute(request);
        } else {
            response = client.execute(request);
        }
        return response;
    }
    protected PayOrderRespDTO parseOrderQuery(String outTradeNo,AlipayTradeQueryResponse response) {
        if (!"10000".equals(response.getCode())) throw new PayClientException(); // 不存在/系统错误均不能证明安全关闭
        if (!Objects.equals(outTradeNo,response.getOutTradeNo())) throw new PayClientException();
        // 2.2 解析订单的状态
        Integer status = parseStatus(response.getTradeStatus());
        if(status==null)throw new PayClientException();
        if(PayOrderStatusEnum.isClosed(status))throw new PayClientException(); // 已付全退也可能TRADE_CLOSED，交给账本/退款查询收敛
        LocalDateTime paid=response.getSendPayDate()==null?null:LocalDateTime.ofInstant(response.getSendPayDate().toInstant(),ZoneId.of("Asia/Shanghai"));
        if(PayOrderStatusEnum.isSuccess(status) && (paid==null || response.getTradeNo()==null))throw new PayClientException();
        PayOrderRespDTO result=PayOrderRespDTO.of(status,response.getTradeNo(),null,paid,outTradeNo,null);
        result.setPrice(parseAmount(response.getTotalAmount()));return result;
    }

    private static Integer parseStatus(String tradeStatus) {
        return Objects.equals("WAIT_BUYER_PAY", tradeStatus) ? PayOrderStatusEnum.WAITING.getStatus()
                : Arrays.asList("TRADE_FINISHED", "TRADE_SUCCESS").contains(tradeStatus) ? PayOrderStatusEnum.SUCCESS.getStatus()
                : Objects.equals("TRADE_CLOSED", tradeStatus) ? PayOrderStatusEnum.CLOSED.getStatus() : null;
    }

    // ============ 退款相关 ==========

    /**
     * 支付宝统一的退款接口 alipay.trade.refund
     *
     * @param reqDTO 退款请求 request DTO
     * @return 退款请求 Response
     */
    @Override
    protected PayRefundRespDTO doUnifiedRefund(PayRefundUnifiedReqDTO reqDTO) throws AlipayApiException {
        // 1.1 构建 AlipayTradeRefundModel 请求
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(reqDTO.getOutTradeNo());
        model.setOutRequestNo(reqDTO.getOutRefundNo());
        model.setRefundAmount(formatAmount(reqDTO.getRefundPrice()));
        model.setRefundReason(reqDTO.getReason());
        // 1.2 构建 AlipayTradePayRequest 请求
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);

        // 2.1 执行请求
        AlipayTradeRefundResponse response;
        if (Objects.equals(config.getMode(), MODE_CERTIFICATE)) {  // 证书模式
            response = client.certificateExecute(request);
        } else {
            response = client.execute(request);
        }
        if (!"10000".equals(response.getCode())) return PayRefundRespDTO.waitingOf(null,reqDTO.getOutRefundNo(),null);
        if(!Objects.equals(reqDTO.getOutTradeNo(),response.getOutTradeNo()))throw new PayClientException();
        // code=10000仅表示请求受理；资金有变动才在此确认，N/缺失沿原号查询。
        // 支付宝同步退款不依赖本项目尚未实现的退款回调。
        if (!"Y".equals(response.getFundChange())) return PayRefundRespDTO.waitingOf(null,reqDTO.getOutRefundNo(),null);
        PayRefundRespDTO result=PayRefundRespDTO.successOf(null,refundTime(response.getGmtRefundPay()),reqDTO.getOutRefundNo(),null);
        if(result.getSuccessTime()==null)return PayRefundRespDTO.waitingOf(null,reqDTO.getOutRefundNo(),null);
        result.setRefundPrice(parseAmount(response.getRefundFee()));
        if(!Objects.equals(result.getRefundPrice(),reqDTO.getRefundPrice()))throw new PayClientException();
        return result;
    }

    @Override
    protected PayRefundRespDTO doGetRefund(String outTradeNo, String outRefundNo) throws AlipayApiException {
        // 1.1 构建 AlipayTradeFastpayRefundQueryModel 请求
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(outTradeNo);
        model.setOutRequestNo(outRefundNo);
        model.setQueryOptions(Collections.singletonList("gmt_refund_pay"));
        // 1.2 构建 AlipayTradeFastpayRefundQueryRequest 请求
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizModel(model);

        // 2.1 执行请求
        AlipayTradeFastpayRefundQueryResponse response;
        if (Objects.equals(config.getMode(), MODE_CERTIFICATE)) { // 证书模式
            response = client.certificateExecute(request);
        } else {
            response = client.execute(request);
        }
        if (!"10000".equals(response.getCode())) return PayRefundRespDTO.waitingOf(null,outRefundNo,null);
        if (!Objects.equals(outTradeNo,response.getOutTradeNo()) || !Objects.equals(outRefundNo,response.getOutRequestNo())) throw new PayClientException();
        // 2.2 创建返回结果
        if (Objects.equals(response.getRefundStatus(), "REFUND_SUCCESS")) {
            PayRefundRespDTO result=PayRefundRespDTO.successOf(null,refundTime(response.getGmtRefundPay()),outRefundNo,null);
            if(result.getSuccessTime()==null)return PayRefundRespDTO.waitingOf(null,outRefundNo,null);
            result.setRefundPrice(parseAmount(response.getRefundAmount()));return result;
        }
        return PayRefundRespDTO.waitingOf(null, outRefundNo, response);
    }

    /**
     * 校验回调数据
     *
     * @param params 回调参数
     * @throws Exception 验签失败时抛出异常
     */
    protected void verifyNotifyData(Map<String, String> params) throws Exception {
        if(params==null || !"RSA2".equals(params.get("sign_type")))throw new PayClientException();
        // SDK会移除sign/sign_type；传副本，原已签字段保留供后续应用/商户核对。
        Map<String,String> signed=new java.util.HashMap<>(params);
        boolean verify;
        if (Objects.equals(config.getMode(), MODE_PUBLIC_KEY)) {
            verify = AlipaySignature.rsaCheckV1(signed, config.getAlipayPublicKey(),
                    StandardCharsets.UTF_8.name(), config.getSignType());
        } else if (Objects.equals(config.getMode(), MODE_CERTIFICATE)) {
            // 由于 rsaCertCheckV1 的第二个参数是 path，所以不能这么调用！！！通过阅读源码，发现可以采用如下方式！
            X509Certificate cert = AntCertificationUtil.getCertFromContent(config.getAlipayPublicCertContent());
            String publicKey = Base64.encodeBase64String(cert.getPublicKey().getEncoded());
            verify = AlipaySignature.rsaCheckV1(signed, publicKey,
                    StandardCharsets.UTF_8.name(), config.getSignType());
        } else {
            throw new IllegalArgumentException("未知的公钥类型：" + config.getMode());
        }
        if(!verify)throw new PayClientException();
    }

    // ========== 各种工具方法 ==========

    protected String formatAmount(Long amount) {
        if(amount==null || amount<=0 || amount>9_007_199_254_740_991L)throw new IllegalArgumentException("渠道金额不正确");
        return BigDecimal.valueOf(amount,2).toPlainString();
    }

    protected String formatTime(LocalDateTime time) {
        return LocalDateTimeUtil.format(time, NORM_DATETIME_FORMATTER);
    }

    protected LocalDateTime parseTime(String str) {
        return str==null || str.isEmpty()?null:LocalDateTimeUtil.parse(str, NORM_DATETIME_FORMATTER);
    }

    /** 上游接口未提供显式关单；只有渠道明确成功才返回关闭，失败/查无单一律待确认。 */
    @Override
    protected PayOrderRespDTO doCloseOrder(String outTradeNo) throws AlipayApiException {
        AlipayTradeCloseModel model=new AlipayTradeCloseModel();model.setOutTradeNo(outTradeNo);
        AlipayTradeCloseRequest request=new AlipayTradeCloseRequest();request.setBizModel(model);
        AlipayTradeCloseResponse response=Objects.equals(config.getMode(),MODE_CERTIFICATE)
                ?client.certificateExecute(request):client.execute(request);
        if(!"10000".equals(response.getCode()) || !Objects.equals(outTradeNo,response.getOutTradeNo()))throw new PayClientException();
        return PayOrderRespDTO.closedOf(null,null,outTradeNo,null);
    }
    protected Long parseAmount(String amount) {
        if(amount==null || !amount.matches("[0-9]{1,14}(\\.[0-9]{1,2})?"))throw new PayClientException();
        long cents=new BigDecimal(amount).movePointRight(2).longValueExact();
        if(cents<=0 || cents>9_007_199_254_740_991L)throw new PayClientException();return cents;
    }
    private LocalDateTime refundTime(java.util.Date time) {
        return time==null?null:LocalDateTime.ofInstant(time.toInstant(),ZoneId.of("Asia/Shanghai"));
    }

}
