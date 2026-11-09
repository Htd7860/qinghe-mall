package cn.qinghe.mall.module.trade.framework.delivery;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;
/** B05计算器资源保护上界；消费者默认50行/999件由结算CartProperties单独约束。 */
@Data
@Validated
@ConfigurationProperties(prefix="qinghe.trade.delivery")
public class DeliveryQuoteProperties {
    @Min(1) @Max(1000) private int maxQuoteLines=200;
    @Min(1) @Max(100000) private int maxItemQuantity=9999;
}
