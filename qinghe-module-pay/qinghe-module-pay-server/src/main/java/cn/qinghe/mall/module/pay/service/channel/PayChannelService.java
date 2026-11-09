package cn.qinghe.mall.module.pay.service.channel;
import cn.qinghe.mall.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.qinghe.mall.module.pay.framework.pay.config.PayChannelProperties;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClient;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClientFactory;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原getPayClient职责；历史尝试必须匹配原渠道版本，不能静默换应用/收款商户。 */
@Service @RequiredArgsConstructor
public class PayChannelService {
    private final PayChannelProperties properties;
    private final PayClientFactory factory;
    public PayClient<AlipayPayClientConfig> client(PayChannelDO channel,Long version) {
        try {
            java.net.URI callback=java.net.URI.create(channel.getNotifyUrl());
            if(!"https".equals(callback.getScheme()) || callback.getHost()==null || callback.getUserInfo()!=null
                    || callback.getFragment()!=null || callback.getQuery()!=null || channel.getNotifyUrl().length()>2048
                    || !("/public-api/pay/notify/alipay/"+channel.getId()).equals(callback.getPath()))
                throw new IllegalArgumentException();
        }catch(RuntimeException invalid){throw exception0(503,"原渠道通知地址配置不正确");}
        PayChannelProperties.Binding binding=properties.getCredentials().get(channel.getConfigRef());
        if(binding==null || binding.getConfig()==null || !Objects.equals(binding.getTenantId(),channel.getTenantId())
                || !Objects.equals(binding.getChannelId(),channel.getId()) || !Objects.equals(binding.getVersion(),version)
                || !Objects.equals(channel.getAuthVersion(),version) || !"alipay_qr".equals(channel.getCode())
                || !Objects.equals(binding.getConfig().getAppId(),channel.getAlipayAppId())
                || !Objects.equals(binding.getConfig().getSellerId(),channel.getSellerId()))throw exception0(503,"原支付渠道配置尚未提供或版本不一致");
        return factory.createAlipayQr(channel.getId(),binding.getConfig());
    }
}
