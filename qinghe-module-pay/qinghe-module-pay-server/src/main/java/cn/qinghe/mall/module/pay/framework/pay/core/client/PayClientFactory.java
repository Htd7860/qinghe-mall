package cn.qinghe.mall.module.pay.framework.pay.core.client;

import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;

/** 择取原客户端工厂创建职责；当前只开放一个沙箱渠道，不注册钱包/转账/微信。
 * @author 芋道源码
 */
public interface PayClientFactory {
    PayClient<AlipayPayClientConfig> createAlipayQr(Long channelId,AlipayPayClientConfig config);
}
