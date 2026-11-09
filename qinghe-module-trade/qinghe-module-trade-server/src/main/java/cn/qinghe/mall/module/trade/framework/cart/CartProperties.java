package cn.qinghe.mall.module.trade.framework.cart;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;
/** B05购物车/结算资源上限；默认100种、50行下单、每SKU999件、报价300秒。 */
@Data @Validated @ConfigurationProperties(prefix="qinghe.trade.checkout")
public class CartProperties {
    @Min(1) @Max(200) private int maxCartLines=100;
    @Min(1) @Max(200) private int maxOrderLines=50;
    @Min(1) @Max(9999) private int maxItemQuantity=999;
    @Min(30) @Max(900) private int quoteTtlSeconds=300;
    @Min(1) @Max(50) private int maxActiveQuotesPerMember=10;
}
