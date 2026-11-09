package cn.qinghe.mall.module.pay.framework.pay.config;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
/** 可信外部配置按不可变configRef登记，不把商家密钥存JSON或返回浏览器；默认空。 */
@Component
@ConfigurationProperties(prefix="qinghe.pay.channels")
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayChannelProperties {
    @JsonIgnore private Map<String,Binding> credentials=new HashMap<>();
    @Data @ToString(onlyExplicitlyIncluded=true)
    public static class Binding {
        private Long tenantId;
        private Long channelId;
        private Long version;
        @JsonIgnore private AlipayPayClientConfig config;
    }
}
