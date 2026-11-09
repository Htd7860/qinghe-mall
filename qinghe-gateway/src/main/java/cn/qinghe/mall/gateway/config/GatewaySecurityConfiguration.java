package cn.qinghe.mall.gateway.config;

import cn.qinghe.mall.gateway.filter.BearerTokenAuthenticationFilter;
import cn.qinghe.mall.gateway.filter.LoginSourceRateLimitFilter;
import cn.qinghe.mall.gateway.util.GatewayResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/** 认证凭据入口由system独立核对；管理接口要求有效身份，资源权限由system复核。 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    public SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http, ObjectMapper objectMapper,
            ReactiveStringRedisTemplate redisTemplate, LoginRateLimitProperties loginRateLimitProperties,
            @Value("${qinghe.gateway.system-base-url}") String systemBaseUrl) {
        http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        // 身份仓库与请求缓存分别配置，避免匿名拒绝仍通过WebSession保存待登录请求。
        http.requestCache().requestCache(NoOpServerRequestCache.getInstance());
        http.httpBasic().disable();
        http.formLogin().disable();
        http.logout().disable();
        // 只接受主动携带的Bearer，不使用Cookie/Basic/默认Session认证。
        http.csrf().disable();
        http.exceptionHandling()
                .authenticationEntryPoint((exchange, ex) -> GatewayResponseUtils.writeError(
                        exchange, objectMapper, HttpStatus.UNAUTHORIZED, "账号未登录"))
                .accessDeniedHandler((exchange, ex) -> GatewayResponseUtils.writeError(
                        exchange, objectMapper, HttpStatus.FORBIDDEN, "没有该操作权限"));
        http.addFilterBefore(new LoginSourceRateLimitFilter(redisTemplate, objectMapper, loginRateLimitProperties),
                SecurityWebFiltersOrder.AUTHENTICATION);
        http.addFilterAt(new BearerTokenAuthenticationFilter(systemBaseUrl, objectMapper),
                SecurityWebFiltersOrder.AUTHENTICATION);
        http.authorizeExchange()
                .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // Provider callback authenticates through the original channel, not member Bearer.
                .pathMatchers(HttpMethod.POST, "/public-api/pay/notify/alipay/{channelId:[0-9]+}").permitAll()
                .pathMatchers(HttpMethod.GET,
                        "/public-api/infra/file/{configId}/get/tenant/{tenantId}/{purpose}/{date}/{filename}").permitAll()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/infra/file/upload", "/platform-api/infra/file/upload").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/infra/file/page", "/platform-api/infra/file/page",
                        "/admin-api/infra/file/get", "/platform-api/infra/file/get",
                        "/admin-api/infra/file/content", "/platform-api/infra/file/content").authenticated()
                .pathMatchers(HttpMethod.DELETE,
                        "/admin-api/infra/file/delete", "/platform-api/infra/file/delete").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/auth/login", "/platform-api/auth/login",
                        "/admin-api/auth/refresh-token", "/platform-api/auth/refresh-token",
                        "/admin-api/auth/logout", "/platform-api/auth/logout").permitAll()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/system/permission/assign-role-menu",
                        "/platform-api/system/permission/assign-role-menu").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/system/role/create", "/platform-api/system/role/create",
                        "/admin-api/system/role/update", "/platform-api/system/role/update",
                        "/admin-api/system/role/update-status", "/platform-api/system/role/update-status",
                        "/admin-api/system/role/delete", "/platform-api/system/role/delete",
                        "/admin-api/system/role/restore", "/platform-api/system/role/restore").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/auth/session", "/platform-api/auth/session").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/role/page", "/platform-api/system/role/page",
                        "/admin-api/system/role/get", "/platform-api/system/role/get").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/menu/simple-list", "/platform-api/system/menu/simple-list",
                        "/admin-api/system/permission/list-role-menus",
                        "/platform-api/system/permission/list-role-menus").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/user/page",
                        "/platform-api/system/user/page",
                        "/admin-api/system/user/get",
                        "/platform-api/system/user/get").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/system/user/create",
                        "/platform-api/system/user/create",
                        "/admin-api/system/user/update",
                        "/platform-api/system/user/update",
                        "/admin-api/system/user/update-status",
                        "/platform-api/system/user/update-status",
                        "/admin-api/system/user/update-password",
                        "/platform-api/system/user/update-password",
                        "/admin-api/system/user/delete",
                        "/platform-api/system/user/delete",
                        "/admin-api/system/user/restore",
                        "/platform-api/system/user/restore").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/dept/list",
                        "/platform-api/system/dept/list").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/system/dept/create",
                        "/platform-api/system/dept/create",
                        "/admin-api/system/dept/update",
                        "/platform-api/system/dept/update",
                        "/admin-api/system/dept/update-status",
                        "/platform-api/system/dept/update-status",
                        "/admin-api/system/dept/delete",
                        "/platform-api/system/dept/delete",
                        "/admin-api/system/dept/restore",
                        "/platform-api/system/dept/restore").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/permission/list-user-roles",
                        "/platform-api/system/permission/list-user-roles").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/system/permission/assign-user-role",
                        "/platform-api/system/permission/assign-user-role",
                        "/admin-api/system/permission/assign-role-data-scope",
                        "/platform-api/system/permission/assign-role-data-scope").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/platform-api/system/tenant/page",
                        "/platform-api/system/tenant/get").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/platform-api/system/tenant/create",
                        "/platform-api/system/tenant/update",
                        "/platform-api/system/tenant/update-status",
                        "/platform-api/system/tenant/delete",
                        "/platform-api/system/tenant/restore").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/platform-api/system/tenant-package/page",
                        "/platform-api/system/tenant-package/get",
                        "/platform-api/system/tenant-package/change-result",
                        "/platform-api/system/tenant-package/change-history").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/platform-api/system/tenant-package/create",
                        "/platform-api/system/tenant-package/update",
                        "/platform-api/system/tenant-package/preview-change",
                        "/platform-api/system/tenant-package/update-status",
                        "/platform-api/system/tenant-package/delete",
                        "/platform-api/system/tenant-package/restore").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/platform-api/system/menu/list").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/platform-api/system/menu/create",
                        "/platform-api/system/menu/update",
                        "/platform-api/system/menu/update-status",
                        "/platform-api/system/menu/delete",
                        "/platform-api/system/menu/restore").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/menu/navigation",
                        "/platform-api/system/menu/navigation").authenticated()
                .pathMatchers(HttpMethod.GET, "/platform-api/system/tenant-package/menu-options").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/system/role/simple-list",
                        "/platform-api/system/role/simple-list",
                        "/admin-api/system/dept/simple-list",
                        "/platform-api/system/dept/simple-list",
                        "/platform-api/system/tenant-package/simple-list").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/product/brand/get",
                        "/admin-api/product/brand/list-all-simple",
                        "/admin-api/product/brand/page",
                        "/admin-api/product/brand/list",
                        "/admin-api/product/category/get",
                        "/admin-api/product/category/list",
                        "/admin-api/product/property/get",
                        "/admin-api/product/property/page",
                        "/admin-api/product/property/simple-list",
                        "/admin-api/product/property/value/get",
                        "/admin-api/product/property/value/page",
                        "/admin-api/product/property/value/simple-list").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/product/brand/create",
                        "/admin-api/product/category/create",
                        "/admin-api/product/property/create",
                        "/admin-api/product/property/value/create").authenticated()
                .pathMatchers(HttpMethod.PUT,
                        "/admin-api/product/brand/update",
                        "/admin-api/product/category/update",
                        "/admin-api/product/property/update",
                        "/admin-api/product/property/value/update").authenticated()
                .pathMatchers(HttpMethod.DELETE,
                        "/admin-api/product/brand/delete",
                        "/admin-api/product/category/delete",
                        "/admin-api/product/property/delete",
                        "/admin-api/product/property/value/delete").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/trade/delivery/express-template/area-options",
                        "/admin-api/trade/delivery/express-template/get",
                        "/admin-api/trade/delivery/express-template/list",
                        "/admin-api/trade/delivery/express-template/list-all-simple",
                        "/admin-api/trade/delivery/express-template/page").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/trade/order-recovery/page", "/admin-api/trade/order-recovery/get",
                        "/admin-api/trade/order-recovery/replay-result", "/admin-api/trade/order-recovery/replay-history").authenticated()
                .pathMatchers(HttpMethod.POST, "/admin-api/trade/order-recovery/replay").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/trade/order-event/page", "/admin-api/trade/order-event/get",
                        "/admin-api/trade/order-event/replay-result", "/admin-api/trade/order-event/replay-history").authenticated()
                .pathMatchers(HttpMethod.POST, "/admin-api/trade/order-event/replay").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/trade/order/page", "/admin-api/trade/order/summary", "/admin-api/trade/order/get-detail",
                        "/admin-api/trade/order/get-delivery-detail", "/admin-api/trade/order/log-page",
                        "/admin-api/trade/order/express-options").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/trade/order/delivery", "/admin-api/trade/order/update-logistics").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/trade/after-sale/page", "/admin-api/trade/after-sale/get-detail",
                        "/admin-api/trade/after-sale/log-page", "/admin-api/trade/after-sale/return-address",
                        "/admin-api/trade/after-sale/decision-result", "/admin-api/trade/after-sale/area-options",
                        "/admin-api/trade/after-sale/refund-result", "/admin-api/trade/after-sale/refund-prepare-result",
                        "/admin-api/trade/after-sale/refund-retry-result", "/admin-api/trade/after-sale/refund-budget",
                        "/admin-api/trade/after-sale/refund-continue-result").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/trade/after-sale/agree", "/admin-api/trade/after-sale/disagree",
                        "/admin-api/trade/after-sale/receive", "/admin-api/trade/after-sale/refund-prepare",
                        "/admin-api/trade/after-sale/refund-execute", "/admin-api/trade/after-sale/refund-sync",
                        "/admin-api/trade/after-sale/refund-retry", "/admin-api/trade/after-sale/refund-continue").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/trade/delivery/express-template/create").authenticated()
                .pathMatchers(HttpMethod.PUT,
                        "/admin-api/trade/delivery/express-template/update").authenticated()
                .pathMatchers(HttpMethod.DELETE,
                        "/admin-api/trade/delivery/express-template/delete").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/product/spu/create",
                        "/admin-api/product/stock/adjust").authenticated()
                .pathMatchers(HttpMethod.PUT,
                        "/admin-api/product/spu/update",
                        "/admin-api/product/spu/update-status").authenticated()
                .pathMatchers(HttpMethod.DELETE,
                        "/admin-api/product/spu/delete").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/admin-api/product/spu/get-detail",
                        "/admin-api/product/spu/page",
                        "/admin-api/product/spu/get-count",
                        "/admin-api/product/stock/operation").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/product/spu/editor-options").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/product/spu/owner-option").authenticated()
                .pathMatchers(HttpMethod.PUT, "/admin-api/product/spu/transfer-owner").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/product/spu/export-excel").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/storefront/resolve").permitAll()
                .pathMatchers(HttpMethod.GET, "/app-api/product/spu/page", "/app-api/product/spu/search-page", "/app-api/product/spu/get-detail",
                        "/app-api/product/catalog/options").permitAll()
                .pathMatchers(HttpMethod.POST, "/app-api/auth/login", "/app-api/auth/refresh-token",
                        "/app-api/auth/logout", "/app-api/auth/register", "/app-api/auth/reset-password",
                        "/app-api/auth/send-code").permitAll()
                .pathMatchers(HttpMethod.GET, "/app-api/auth/session").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/promotion/seckill-activity/page",
                        "/app-api/promotion/seckill-activity/get").permitAll()
                .pathMatchers(HttpMethod.GET, "/app-api/trade/seckill/quote-result",
                        "/app-api/promotion/seckill/request-result", "/app-api/promotion/seckill/request-detail",
                        "/app-api/promotion/seckill/request-page").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/trade/seckill/quote", "/app-api/promotion/seckill/admit").authenticated()
                .pathMatchers(HttpMethod.POST, "/platform-api/promotion/seckill-operation/stop").authenticated()
                .pathMatchers(HttpMethod.GET, "/platform-api/promotion/seckill-operation/page",
                        "/platform-api/promotion/seckill-operation/audit-page").authenticated()
                .pathMatchers(HttpMethod.POST, "/admin-api/promotion/seckill-activity/prepare-runtime",
                        "/admin-api/promotion/seckill-activity/initialize-runtime", "/admin-api/promotion/seckill-activity/ready-runtime",
                        "/admin-api/promotion/seckill-activity/stop-runtime").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/promotion/seckill-activity/runtime-result").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/promotion/seckill-activity/publish-result",
                        "/admin-api/promotion/seckill-activity/publish-history",
                        "/admin-api/promotion/seckill-activity/clearing-progress").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/admin-api/promotion/seckill-activity/prepare-publish", "/admin-api/promotion/seckill-activity/advance-publish-sku",
                        "/admin-api/promotion/seckill-activity/cancel-publish", "/admin-api/promotion/seckill-activity/compensate-publish-sku",
                        "/admin-api/promotion/seckill-activity/confirm-publish", "/admin-api/promotion/seckill-activity/withdraw-publish",
                        "/admin-api/promotion/seckill-activity/settle-publish", "/admin-api/promotion/seckill-activity/reopen-draft").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/promotion/seckill-activity/get",
                        "/admin-api/promotion/seckill-activity/page").authenticated()
                .pathMatchers(HttpMethod.POST, "/admin-api/promotion/seckill-activity/create").authenticated()
                .pathMatchers(HttpMethod.PUT, "/admin-api/promotion/seckill-activity/update").authenticated()
                .pathMatchers(HttpMethod.GET, "/admin-api/promotion/coupon-template/get",
                        "/admin-api/promotion/coupon-template/page").authenticated()
                .pathMatchers(HttpMethod.POST, "/admin-api/promotion/coupon-template/create").authenticated()
                .pathMatchers(HttpMethod.PUT, "/admin-api/promotion/coupon-template/update",
                        "/admin-api/promotion/coupon-template/update-status").authenticated()
                .pathMatchers(HttpMethod.DELETE, "/admin-api/promotion/coupon-template/delete").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/member/address/get", "/app-api/member/address/list",
                        "/app-api/member/address/get-default", "/app-api/member/address/area-options",
                        "/app-api/member/user/get").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/member/address/create").authenticated()
                .pathMatchers(HttpMethod.PUT, "/app-api/member/address/update", "/app-api/member/user/preferences").authenticated()
                .pathMatchers(HttpMethod.DELETE, "/app-api/member/address/delete").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/promotion/coupon-template/page", "/app-api/promotion/coupon/page",
                        "/app-api/promotion/coupon/get", "/app-api/promotion/coupon/take-result").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/promotion/coupon/take").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/trade/cart/list", "/app-api/trade/cart/get-count", "/app-api/trade/cart/product-options").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/trade/cart/add").authenticated()
                .pathMatchers(HttpMethod.PUT, "/app-api/trade/cart/update-count", "/app-api/trade/cart/update-selected", "/app-api/trade/cart/reset").authenticated()
                .pathMatchers(HttpMethod.DELETE, "/app-api/trade/cart/delete").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/trade/order/settlement").authenticated()
                .pathMatchers(HttpMethod.GET,
                        "/app-api/trade/after-sale/page", "/app-api/trade/after-sale/get",
                        "/app-api/trade/after-sale/log-page", "/app-api/trade/after-sale/return-address",
                        "/app-api/trade/after-sale/application-result", "/app-api/trade/after-sale/decision-result",
                        "/app-api/trade/after-sale/express-options").authenticated()
                .pathMatchers(HttpMethod.POST,
                        "/app-api/trade/after-sale/create", "/app-api/trade/after-sale/delivery",
                        "/app-api/trade/after-sale/cancel").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/trade/order/receive").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/trade/order/settlement-result").authenticated()
                .pathMatchers(HttpMethod.GET, "/app-api/trade/order/create-result", "/app-api/trade/order/page",
                        "/app-api/trade/order/get-detail", "/app-api/trade/order/payment-result").authenticated()
                .pathMatchers(HttpMethod.POST, "/app-api/trade/order/create", "/app-api/trade/order/stop-create", "/app-api/trade/order/prepare-payment",
                        "/app-api/trade/order/cancel", "/app-api/trade/order/submit-payment", "/app-api/trade/order/query-payment",
                        "/app-api/trade/order/recover-payment-qr").authenticated()
                .anyExchange().denyAll();
        return http.build();
    }
}
