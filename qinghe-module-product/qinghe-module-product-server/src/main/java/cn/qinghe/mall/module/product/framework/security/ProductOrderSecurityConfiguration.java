package cn.qinghe.mall.module.product.framework.security;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.config.OrderServiceAuthProperties;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.module.product.controller.internal.stock.InternalOrderStockController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.util.Arrays;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties({OrderServiceAuthProperties.class,ProductSeckillAuthProperties.class})
public class ProductOrderSecurityConfiguration {
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(SecurityProperties security, OAuth2TokenCommonApi userTokens,
            AuthenticationEntryPoint entry, ObjectMapper json, OrderServiceAuthProperties services,ProductSeckillAuthProperties seckill) {
        return new ProductCommandAuthenticationFilter(security, userTokens, entry, json, services.getTradeToken(),
                Arrays.asList(InternalOrderStockController.DEDUCT, InternalOrderStockController.RESULT,
                        InternalOrderStockController.CLOSE_CREATION,InternalOrderStockController.CLOSE_UNPAID,
                        InternalOrderStockController.RESTORE_REFUND,InternalOrderStockController.REFUND_RESULT),seckill.getServiceToken());
    }
}
