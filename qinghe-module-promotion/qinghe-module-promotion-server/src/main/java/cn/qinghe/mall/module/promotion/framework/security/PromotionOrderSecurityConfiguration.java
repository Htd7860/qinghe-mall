package cn.qinghe.mall.module.promotion.framework.security;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.config.OrderServiceAuthProperties;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.framework.security.core.filter.TradeCommandAuthenticationFilter;
import cn.qinghe.mall.module.promotion.controller.internal.coupon.InternalOrderCouponController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.util.Arrays;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(OrderServiceAuthProperties.class)
public class PromotionOrderSecurityConfiguration {
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(SecurityProperties security, OAuth2TokenCommonApi userTokens,
            AuthenticationEntryPoint entry, ObjectMapper json, OrderServiceAuthProperties services) {
        return new TradeCommandAuthenticationFilter(security, userTokens, entry, json, services.getTradeToken(),
                Arrays.asList(InternalOrderCouponController.RESERVE, InternalOrderCouponController.USE, InternalOrderCouponController.RESULT,
                        InternalOrderCouponController.CLOSE_CREATION,InternalOrderCouponController.CLOSE_UNPAID,InternalOrderCouponController.RETURN_REFUND,
                        cn.qinghe.mall.module.promotion.controller.internal.seckill.InternalSeckillEventController.READ,
                        cn.qinghe.mall.module.promotion.controller.internal.seckill.InternalSeckillEventController.RESOURCE,
                        cn.qinghe.mall.module.promotion.controller.internal.seckill.InternalSeckillEventController.PAYMENT_ELIGIBILITY,
                        cn.qinghe.mall.module.promotion.service.seckill.SeckillRefundCommandContext.RECOVER,
                        cn.qinghe.mall.module.promotion.service.seckill.SeckillRefundCommandContext.RESULT));
    }
}
