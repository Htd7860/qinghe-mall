package cn.qinghe.mall.module.trade.framework.security;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.*;
import cn.qinghe.mall.module.trade.controller.internal.seckill.InternalSeckillQuoteBindingController;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.util.Arrays;

/** 直接复用现有精确POST服务认证过滤器，其他请求仍执行原会员/员工Bearer验证。 */
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(TradeSeckillBindingProperties.class)
public class TradeSeckillBindingSecurityConfiguration {
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(SecurityProperties security,OAuth2TokenCommonApi api,
            AuthenticationEntryPoint entry,ObjectMapper json,TradeSeckillBindingProperties settings,OrderResourceProperties outbound) {
        String token=settings.getServiceToken();
        if (token!=null && !token.isEmpty() && Arrays.asList(outbound.getProductToken(),outbound.getPromotionToken(),outbound.getPayToken()).contains(token))
            throw new IllegalArgumentException("秒杀报价绑定服务不能复用普通交易出站凭据");
        return new TradeCommandAuthenticationFilter(security,api,entry,json,token,Arrays.asList(
                InternalSeckillQuoteBindingController.BIND,InternalSeckillQuoteBindingController.RESULT,InternalSeckillQuoteBindingController.CLOSE,
                InternalSeckillQuoteBindingController.ORDER_RESULT,InternalSeckillQuoteBindingController.ORDER_STATE,InternalSeckillQuoteBindingController.REFUND_STATE));
    }
}
