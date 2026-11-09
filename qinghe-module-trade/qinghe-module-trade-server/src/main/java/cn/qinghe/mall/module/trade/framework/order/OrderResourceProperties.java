package cn.qinghe.mall.module.trade.framework.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 各接收服务独立凭据，不复用消费者Token或system会员服务凭据。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.trade.order-resources")
public class OrderResourceProperties {
    @JsonIgnore private String productToken = "";
    @JsonIgnore private String promotionToken = "";
    @JsonIgnore private String payToken = "";
}
