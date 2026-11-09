package cn.qinghe.mall.module.product.framework.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 单独的promotion→product凭据；空值关闭秒杀库存服务入口，不复用trade凭据。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.product.seckill-stock")
public class ProductSeckillAuthProperties {
    @JsonIgnore
    private String serviceToken="";
}
