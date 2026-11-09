package cn.qinghe.mall.module.trade.framework.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** promotion→trade报价绑定专用凭据；空值关闭入口，不复用普通交易出站密钥。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.trade.seckill-binding")
public class TradeSeckillBindingProperties {
    @JsonIgnore private String serviceToken="";
}
