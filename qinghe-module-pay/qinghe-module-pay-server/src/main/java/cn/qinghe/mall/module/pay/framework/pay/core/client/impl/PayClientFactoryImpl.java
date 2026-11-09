package cn.qinghe.mall.module.pay.framework.pay.core.client.impl;

import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClient;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClientFactory;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayQrPayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.validation.Validator;

/** 原createPayClient→init职责。调用者须来自可信固定渠道配置，不从公众JSON构造配置。
 * 暂无全局无界客户端缓存；后续渠道服务按持久配置版本持有客户端，不共享跨商家密钥。
 * @author 芋道源码
 */
@Component
@RequiredArgsConstructor
public class PayClientFactoryImpl implements PayClientFactory {
    private final Validator validator;
    @Override
    public PayClient<AlipayPayClientConfig> createAlipayQr(Long channelId,AlipayPayClientConfig config) {
        AlipayPayClientConfig snapshot=BeanUtils.toBean(config,AlipayPayClientConfig.class);
        AlipayQrPayClient client=new AlipayQrPayClient(channelId,snapshot,validator);
        client.init();
        return client;
    }
}
