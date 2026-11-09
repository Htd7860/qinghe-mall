package cn.qinghe.mall.gateway.filter;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import cn.qinghe.mall.gateway.util.GatewayResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** 每次向system确认身份；仅加入Security链，不另外注册成全局WebFilter Bean。 */
public class BearerTokenAuthenticationFilter implements WebFilter {

    private static final ParameterizedTypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>> RESULT_TYPE =
            new ParameterizedTypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>>() { };

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public BearerTokenAuthenticationFilter(String systemBaseUrl, ObjectMapper objectMapper) {
        URI base = URI.create(systemBaseUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("system认证地址配置不正确");
        }
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(base.toString())
                .filter((request,next) -> Mono.deferContextual(context -> {
                    String trace=context.getOrDefault(TraceUtils.KEY,"");
                    org.springframework.web.reactive.function.client.ClientRequest.Builder builder=
                            org.springframework.web.reactive.function.client.ClientRequest.from(request);
                    builder.headers(headers -> {
                        headers.remove(TraceUtils.HEADER);
                        if(TraceUtils.valid(trace))headers.set(TraceUtils.HEADER,trace);
                    });
                    return next.exchange(builder.build());
                }))
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(64 * 1024)).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 丢弃外部自报身份；后续内部服务仍根据原Bearer独立校验，不信任login-user头。
        ServerWebExchange clean = exchange.mutate().request(builder -> builder.headers(headers -> {
            headers.remove("login-user");
            headers.remove("login-user-id");
            headers.remove("login-user-type");
            headers.remove("user-id");
            headers.remove("user-type");
            headers.remove("visit-tenant-id");
            headers.remove("tenant-ignore");
            headers.remove(HttpHeaders.COOKIE);
        })).build();
        String token;
        try {
            token = obtainBearer(clean.getRequest().getHeaders());
        } catch (BadCredentialsException ex) {
            return reject(clean, HttpStatus.UNAUTHORIZED, "认证信息格式不正确");
        }
        if (token == null) {
            return chain.filter(clean);
        }
        String path = clean.getRequest().getPath().pathWithinApplication().value();
        if (clean.getRequest().getMethod() == HttpMethod.POST
                && ("/admin-api/auth/logout".equals(path)
                || "/platform-api/auth/logout".equals(path)
                || "/app-api/auth/logout".equals(path))) {
            // 只传递已核对格式的原Bearer，不建立身份；对应服务按固定client撤销关联会话。
            // 普通校验会提前拒绝过期/撤销Token，导致无法退出或重试提交后的缓存清理。
            return chain.filter(clean);
        }
        // 错误处理只包围认证，不把下游业务异常转换成Token错误。
        return checkToken(token).map(user -> {
            checkRequestScope(clean, user);
            return user;
        }).onErrorResume(AuthenticationException.class, ex -> reject(clean,
                ex instanceof BadCredentialsException ? HttpStatus.UNAUTHORIZED : HttpStatus.SERVICE_UNAVAILABLE,
                ex instanceof BadCredentialsException ? "访问令牌无效或已过期" : "认证服务暂不可用")
                .then(Mono.empty()))
                .onErrorResume(AccessDeniedException.class,
                        ex -> reject(clean, HttpStatus.FORBIDDEN, "无权访问该入口或租户").then(Mono.empty()))
                .flatMap(user -> {
                    ServerWebExchange trusted = clean.mutate().request(builder -> builder.headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.set("tenant-id", user.getTenantId().toString());
                    })).build();
                    // 未接RBAC，不伪造权限；这里只建立已认证身份。
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                    return chain.filter(trusted).contextWrite(
                            ReactiveSecurityContextHolder.withAuthentication(authentication));
                });
    }

    private Mono<OAuth2AccessTokenCheckRespDTO> checkToken(String token) {
        return webClient.get().uri("/internal-api/auth/token/check")
                .headers(headers -> headers.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .<OAuth2AccessTokenCheckRespDTO>exchangeToMono(response -> {
                    if (response.rawStatusCode() == 401) {
                        return response.releaseBody().then(Mono.error(new BadCredentialsException("无效凭据")));
                    }
                    if (response.rawStatusCode() != 200) {
                        return response.releaseBody().then(Mono.error(
                                new AuthenticationServiceException("认证服务响应异常")));
                    }
                    return response.bodyToMono(RESULT_TYPE).map(result -> {
                        if (Integer.valueOf(401).equals(result.getCode())) {
                            throw new BadCredentialsException("无效凭据");
                        }
                        OAuth2AccessTokenCheckRespDTO user = result.getData();
                        if (!result.isSuccess() || user == null || user.getUserId() == null
                                || user.getUserId() <= 0 || user.getTenantId() == null || user.getTenantId() < 0
                                || !(Integer.valueOf(1).equals(user.getUserType())
                                || Integer.valueOf(2).equals(user.getUserType()))
                                || user.getExpiresTime() == null) {
                            throw new AuthenticationServiceException("认证身份响应异常");
                        }
                        if (!user.getExpiresTime().isAfter(LocalDateTime.now())) {
                            throw new BadCredentialsException("凭据已过期");
                        }
                        return user;
                    });
                }).switchIfEmpty(Mono.error(new AuthenticationServiceException("认证响应为空")))
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(ex -> ex instanceof AuthenticationException ? ex
                        : new AuthenticationServiceException("认证服务暂不可用"));
    }

    private String obtainBearer(HttpHeaders headers) {
        List<String> values = headers.get(HttpHeaders.AUTHORIZATION);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() != 1) {
            throw new BadCredentialsException("重复认证头");
        }
        String value = values.get(0).trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BadCredentialsException("认证类型不正确");
        }
        String token = value.substring(7).trim();
        if (token.isEmpty() || token.length() > 128 || token.chars().anyMatch(ch ->
                Character.isWhitespace(ch) || Character.isSpaceChar(ch) || Character.isISOControl(ch) || ch == ',')) {
            throw new BadCredentialsException("凭据格式不正确");
        }
        return token;
    }

    private void checkRequestScope(ServerWebExchange exchange, OAuth2AccessTokenCheckRespDTO user) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if ((path.startsWith("/admin-api/") && (user.getUserType() != 2 || user.getTenantId() <= 0))
                || (path.startsWith("/platform-api/") && (user.getUserType() != 2 || user.getTenantId() != 0))
                || (path.startsWith("/app-api/") && (user.getUserType() != 1 || user.getTenantId() <= 0))) {
            throw new AccessDeniedException("入口身份不匹配");
        }
        List<String> tenants = exchange.getRequest().getHeaders().get("tenant-id");
        if (tenants != null && (tenants.size() != 1
                || !user.getTenantId().toString().equals(tenants.get(0)))) {
            throw new AccessDeniedException("请求租户不匹配");
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        return GatewayResponseUtils.writeError(exchange, objectMapper, status, message);
    }
}
