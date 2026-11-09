package cn.qinghe.mall.module.trade;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import cn.qinghe.mall.module.trade.framework.delivery.DeliveryQuoteProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
/** 独立交易Servlet实例，配送模板与当前会员购物车。 */
@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({DeliveryQuoteProperties.class,cn.qinghe.mall.module.trade.framework.cart.CartProperties.class,cn.qinghe.mall.module.trade.framework.order.OrderResourceProperties.class})
@org.springframework.cloud.openfeign.EnableFeignClients(clients={cn.qinghe.mall.module.trade.framework.member.TradeStorefrontApi.class,cn.qinghe.mall.module.trade.framework.product.TradeProductApi.class,cn.qinghe.mall.module.trade.framework.checkout.CheckoutMemberApi.class,cn.qinghe.mall.module.trade.framework.checkout.CheckoutCouponApi.class,cn.qinghe.mall.module.trade.framework.order.OrderStockFeignApi.class,cn.qinghe.mall.module.trade.framework.order.OrderCouponFeignApi.class,cn.qinghe.mall.module.trade.framework.order.OrderPayFeignApi.class})
public class TradeServerApplication {
    public static void main(String[] args) { SpringApplication.run(TradeServerApplication.class,args); }
}
