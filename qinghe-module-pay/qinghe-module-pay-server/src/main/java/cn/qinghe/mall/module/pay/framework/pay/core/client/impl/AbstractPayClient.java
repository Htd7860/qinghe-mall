package cn.qinghe.mall.module.pay.framework.pay.core.client.impl;

import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClient;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClientConfig;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.exception.PayClientException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.validation.Validator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.Semaphore;

/** 择取原AbstractPayClient模板职责；使用现有Validator，不反复创建ValidatorFactory。
 * 不打印请求/通知/SDK响应；不在数据库事务内访问渠道，有限并发，无自动重试。
 * 客户端及config由后续固定渠道配置服务构造，非公众可自由创建对象。
 * @author 芋道源码
 */
public abstract class AbstractPayClient<Config extends PayClientConfig> implements PayClient<Config> {
    private final Long channelId;
    private final Validator validator;
    private static final Semaphore CALLS=new Semaphore(16);
    protected final Config config;
    protected AbstractPayClient(Long channelId,Config config,Validator validator) {
        if(channelId==null || channelId<=0 || config==null || validator==null)throw new IllegalArgumentException("支付客户端配置不完整");
        this.channelId=channelId;this.config=config;this.validator=validator;config.validate(validator);
    }
    @Override public Long getId(){return channelId;}
    @Override public Config getConfig(){return config;}
    public final void init(){invoke(() -> {doInit();return null;});}
    protected abstract void doInit() throws Exception;
    @Override public final PayOrderRespDTO unifiedOrder(PayOrderUnifiedReqDTO request){
        validate(request);businessNo(request.getOutTradeNo());
        callback(request.getNotifyUrl());
        if(request.getReturnUrl()!=null && !request.getReturnUrl().isEmpty())callback(request.getReturnUrl());
        return invoke(() -> doUnifiedOrder(request));
    }
    @Override public final PayOrderRespDTO getOrder(String no){businessNo(no);return invoke(() -> doGetOrder(no));}
    @Override public final PayOrderRespDTO recoverQr(PayOrderUnifiedReqDTO request){
        validate(request);businessNo(request.getOutTradeNo());callback(request.getNotifyUrl());
        if(request.getReturnUrl()!=null && !request.getReturnUrl().isEmpty())callback(request.getReturnUrl());
        return invoke(() -> doRecoverQr(request));
    }
    @Override public final PayOrderRespDTO closeOrder(String no){businessNo(no);return invoke(() -> doCloseOrder(no));}
    @Override public final PayOrderRespDTO parseOrderNotify(Map<String,String> params,String body,Map<String,String> headers){
        if(params==null || params.size()>100)throw new IllegalArgumentException("支付通知字段不完整");
        Map<String,String> copy=new HashMap<>();
        for(Map.Entry<String,String> item:params.entrySet()) {
            if(item.getKey()==null || item.getKey().length()>128 || item.getValue()==null || item.getValue().length()>16384)
                throw new IllegalArgumentException("支付通知字段不正确");
            copy.put(item.getKey(),item.getValue());
        }
        // 验签和解析同一份参数，不能验params后改信另一个body。HTTP层仍须拒绝重复字段。
        return invoke(() -> doParseOrderNotify(Collections.unmodifiableMap(copy),null,Collections.emptyMap()));
    }
    @Override public final PayRefundRespDTO unifiedRefund(PayRefundUnifiedReqDTO request){
        validate(request);businessNo(request.getOutTradeNo());businessNo(request.getOutRefundNo());
        if(request.getRefundPrice()>request.getPayPrice())throw new IllegalArgumentException("退款不能超过原付款金额");
        return invoke(() -> doUnifiedRefund(request));
    }
    @Override public final PayRefundRespDTO getRefund(String order,String refund){businessNo(order);businessNo(refund);return invoke(() -> doGetRefund(order,refund));}
    @Override public final PayRefundRespDTO parseRefundNotify(Map<String,String> params,String body,Map<String,String> headers){
        throw new UnsupportedOperationException("支付宝退款使用原退款号查询确认");
    }
    protected abstract PayOrderRespDTO doUnifiedOrder(PayOrderUnifiedReqDTO request) throws Exception;
    protected PayOrderRespDTO doRecoverQr(PayOrderUnifiedReqDTO request) throws Exception {throw new PayClientException();}
    protected abstract PayOrderRespDTO doGetOrder(String no) throws Exception;
    protected abstract PayOrderRespDTO doCloseOrder(String no) throws Exception;
    protected abstract PayOrderRespDTO doParseOrderNotify(Map<String,String> params,String body,Map<String,String> headers) throws Exception;
    protected abstract PayRefundRespDTO doUnifiedRefund(PayRefundUnifiedReqDTO request) throws Exception;
    protected abstract PayRefundRespDTO doGetRefund(String order,String refund) throws Exception;
    private void callback(String value) {
        try {
            java.net.URI uri=java.net.URI.create(value);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException();
        }catch(RuntimeException error){throw new IllegalArgumentException("渠道回调地址须由可信配置提供HTTPS地址");}
    }
    private void validate(Object request){if(request==null || !validator.validate(request).isEmpty())throw new IllegalArgumentException("支付渠道参数不正确");}
    protected void businessNo(String no){if(no==null || !no.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("渠道业务号不正确");}
    private <T> T invoke(Operation<T> operation) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("渠道调用不能置于数据库事务内");
        if(!CALLS.tryAcquire())throw new PayClientException();
        try {return operation.run();}catch(PayClientException error){throw error;}catch(Exception error){throw new PayClientException();}
        finally{CALLS.release();}
    }
    private interface Operation<T>{T run() throws Exception;}
}
