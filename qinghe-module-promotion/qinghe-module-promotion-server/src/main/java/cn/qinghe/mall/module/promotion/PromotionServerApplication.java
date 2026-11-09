package cn.qinghe.mall.module.promotion;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
/** 独立优惠券服务；管理与消费者精确HTTP使用既有Bearer链。 */
@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
@org.springframework.cloud.openfeign.EnableFeignClients(clients={cn.qinghe.mall.module.promotion.framework.product.CouponProductScopeApi.class,cn.qinghe.mall.module.promotion.framework.member.CouponStorefrontApi.class,cn.qinghe.mall.module.promotion.framework.product.SeckillProductApi.class,cn.qinghe.mall.module.promotion.framework.product.SeckillStockApi.class,cn.qinghe.mall.module.promotion.framework.trade.SeckillQuoteBindingApi.class})
public class PromotionServerApplication {
    public static void main(String[] args) { SpringApplication.run(PromotionServerApplication.class,args); }
}
