package cn.qinghe.mall.framework.security.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** product/promotion 各自配置独立的 trade 凭据；空值关闭内部订单写入口。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.order-service-auth")
public class OrderServiceAuthProperties {
    @JsonIgnore
    private String tradeToken = "";
}
