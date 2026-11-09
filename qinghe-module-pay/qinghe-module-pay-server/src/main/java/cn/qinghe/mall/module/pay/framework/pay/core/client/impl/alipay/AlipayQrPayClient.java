package cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay;

import javax.validation.Validator;
import cn.qinghe.mall.module.pay.framework.pay.core.client.exception.PayClientException;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.enums.PayOrderDisplayModeEnum;
import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;

import java.util.Objects;

import static cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig.MODE_CERTIFICATE;

/**
 * 支付宝【扫码支付】的 PayClient 实现类
 *
 * 文档：<a href="https://opendocs.alipay.com/apis/02890k">扫码支付</a>
 *
 * @author 芋道源码
 */
public class AlipayQrPayClient extends AbstractAlipayPayClient {

    public AlipayQrPayClient(Long channelId, AlipayPayClientConfig config, Validator validator) {
        super(channelId, config, validator);
    }

    @Override
    protected PayOrderRespDTO doRecoverQr(PayOrderUnifiedReqDTO request) throws Exception {
        // 从不把查无交易解释成CLOSED，也不生成新的out_trade_no。
        AlipayTradeQueryResponse response=queryOrder(request.getOutTradeNo());
        if("10000".equals(response.getCode())) {
            PayOrderRespDTO current=parseOrderQuery(request.getOutTradeNo(),response);
            if(!Objects.equals(current.getPrice(),request.getPrice()))throw new PayClientException();
            if(current.getStatus()==10)return current;
            if(current.getStatus()!=0)throw new PayClientException();
        }else if(!"40004".equals(response.getCode()) || !"ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
            throw new PayClientException(); // 网络/签名/权限/系统错误一律停止本次重取。
        }
        return doUnifiedOrder(request);
    }

    @Override
    protected PayOrderRespDTO doUnifiedOrder(PayOrderUnifiedReqDTO reqDTO) throws AlipayApiException {
        businessNo(reqDTO.getOutTradeNo());
        if(!java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).isBefore(reqDTO.getExpireTime()))throw new IllegalArgumentException("原付款期限已到期");
        // 1.1 构建 AlipayTradePrecreateModel 请求
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        // ① 通用的参数
        model.setOutTradeNo(reqDTO.getOutTradeNo());
        model.setSubject(reqDTO.getSubject());
        model.setBody(reqDTO.getBody());
        model.setTotalAmount(formatAmount(reqDTO.getPrice()));
        model.setTimeExpire(formatTime(reqDTO.getExpireTime()));
        model.setProductCode("FACE_TO_FACE_PAYMENT"); // 销售产品码. 目前扫码支付场景下仅支持 FACE_TO_FACE_PAYMENT
        // ② 个性化的参数【无】
        // ③ 支付宝扫码支付只有一种展示，考虑到前端可能希望二维码扫描后，手机打开
        String displayMode = PayOrderDisplayModeEnum.QR_CODE.getMode();

        // 1.2 构建 AlipayTradePrecreateRequest 请求
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setBizModel(model);
        request.setNotifyUrl(reqDTO.getNotifyUrl());
        request.setReturnUrl(reqDTO.getReturnUrl());

        // 2.1 执行请求
        AlipayTradePrecreateResponse response;
        if (Objects.equals(config.getMode(), MODE_CERTIFICATE)) {
            // 证书模式
            response = client.certificateExecute(request);
        } else {
            response = client.execute(request);
        }
        // 2.2 处理结果
        if (!"10000".equals(response.getCode())) {
            // 重取期间可能刚好付款：改查原号，不以错误文案直接入账。
            if("40004".equals(response.getCode()) && "ACQ.TRADE_HAS_SUCCESS".equals(response.getSubCode())) {
                try{return doGetOrder(reqDTO.getOutTradeNo());}catch(Exception unknown){throw new PayClientException();}
            }
            throw new PayClientException(); // 拒绝/网络未知不能作为整笔支付单安全关闭的证明
        }
        if(!Objects.equals(reqDTO.getOutTradeNo(),response.getOutTradeNo()) || response.getQrCode()==null || response.getQrCode().isEmpty())throw new PayClientException();
        PayOrderRespDTO result=PayOrderRespDTO.waitingOf(displayMode,response.getQrCode(),reqDTO.getOutTradeNo(),null);
        result.setPrice(reqDTO.getPrice());return result;
    }
}
