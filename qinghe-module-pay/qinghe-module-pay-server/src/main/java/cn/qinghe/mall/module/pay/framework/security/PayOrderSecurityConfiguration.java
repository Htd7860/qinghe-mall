package cn.qinghe.mall.module.pay.framework.security;
import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.security.config.OrderServiceAuthProperties;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.filter.TokenAuthenticationFilter;
import cn.qinghe.mall.framework.security.core.filter.TradeCommandAuthenticationFilter;
import cn.qinghe.mall.module.pay.controller.internal.order.InternalPayOrderController;
import cn.qinghe.mall.module.pay.controller.internal.refund.InternalPayRefundController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.util.Arrays;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(OrderServiceAuthProperties.class)
public class PayOrderSecurityConfiguration {
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(SecurityProperties security,OAuth2TokenCommonApi tokens,
            AuthenticationEntryPoint entry,ObjectMapper json,OrderServiceAuthProperties services) {
        return new TradeCommandAuthenticationFilter(security,tokens,entry,json,services.getTradeToken(),
                Arrays.asList(InternalPayOrderController.CREATE,InternalPayOrderController.RESULT,InternalPayOrderController.CLOSE,InternalPayOrderController.SYNCHRONIZE,
                        InternalPayOrderController.SUBMIT,InternalPayOrderController.SUBMISSION,InternalPayOrderController.RECOVER_QR,
                        InternalPayRefundController.CREATE,InternalPayRefundController.RESULT,InternalPayRefundController.SUBMIT,
                        InternalPayRefundController.QUERY,InternalPayRefundController.RETRY,InternalPayRefundController.CONTINUE,
                        InternalPayRefundController.CONTINUATION_RESULT)) {
            @Override
            protected boolean shouldNotFilter(javax.servlet.http.HttpServletRequest request) {
                return "POST".equals(request.getMethod())
                        && request.getServletPath().matches("/public-api/pay/notify/alipay/[1-9][0-9]{0,15}");
            }
        };
    }
}
