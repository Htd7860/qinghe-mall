package cn.qinghe.mall.gateway.filter;

import cn.hutool.crypto.SecureUtil;
import cn.qinghe.mall.gateway.config.LoginRateLimitProperties;
import cn.qinghe.mall.gateway.util.GatewayResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 登录/刷新/退出共用来源额度，再执行凭据处理。只在安全链中注册一次。 */
public class LoginSourceRateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginSourceRateLimitFilter.class);
    private static final PathPattern ADMIN_LOGIN = new PathPatternParser().parse("/admin-api/auth/login");
    private static final PathPattern PLATFORM_LOGIN = new PathPatternParser().parse("/platform-api/auth/login");
    private static final PathPattern ADMIN_REFRESH = new PathPatternParser().parse("/admin-api/auth/refresh-token");
    private static final PathPattern PLATFORM_REFRESH = new PathPatternParser().parse("/platform-api/auth/refresh-token");
    private static final PathPattern ADMIN_LOGOUT = new PathPatternParser().parse("/admin-api/auth/logout");
    private static final PathPattern PLATFORM_LOGOUT = new PathPatternParser().parse("/platform-api/auth/logout");
    // 仅扩展来源保护匹配，未增加安全放行或代理路由。
    private static final PathPattern APP_LOGIN = new PathPatternParser().parse("/app-api/auth/login");
    private static final PathPattern APP_REFRESH = new PathPatternParser().parse("/app-api/auth/refresh-token");
    private static final PathPattern APP_LOGOUT = new PathPatternParser().parse("/app-api/auth/logout");
    private static final PathPattern APP_REGISTER = new PathPatternParser().parse("/app-api/auth/register");
    private static final PathPattern APP_RESET_PASSWORD = new PathPatternParser().parse("/app-api/auth/reset-password");
    private static final PathPattern APP_SEND_CODE = new PathPatternParser().parse("/app-api/auth/send-code");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> script;
    private final int maxRequests;
    private final int maxLocalSources;
    private final long windowMillis;
    private final long windowNanos;
    private final Duration redisTimeout;
    private final String keyPrefix;
    // 固定时长且不续期，插入顺序就是到期顺序；仅从队首回收，不淘汰仍有效的记录。
    private final Map<String, LocalWindow> localWindows = new LinkedHashMap<>();
    private final AtomicBoolean redisUnavailable = new AtomicBoolean();

    public LoginSourceRateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper, LoginRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxRequests = properties.getMaxRequests();
        this.maxLocalSources = properties.getMaxLocalSources();
        this.windowMillis = TimeUnit.SECONDS.toMillis(properties.getWindowSeconds());
        this.windowNanos = TimeUnit.SECONDS.toNanos(properties.getWindowSeconds());
        this.redisTimeout = Duration.ofMillis(properties.getRedisTimeoutMillis());
        this.keyPrefix = properties.getKeyPrefix() + ":gateway:login-source:";
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("redis/login-source-limit.lua"));
        this.script.setResultType(Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST
                || !(ADMIN_LOGIN.matches(exchange.getRequest().getPath().pathWithinApplication())
                || PLATFORM_LOGIN.matches(exchange.getRequest().getPath().pathWithinApplication())
                || ADMIN_REFRESH.matches(exchange.getRequest().getPath().pathWithinApplication())
                || PLATFORM_REFRESH.matches(exchange.getRequest().getPath().pathWithinApplication())
                || ADMIN_LOGOUT.matches(exchange.getRequest().getPath().pathWithinApplication())
                || PLATFORM_LOGOUT.matches(exchange.getRequest().getPath().pathWithinApplication())
                || APP_LOGIN.matches(exchange.getRequest().getPath().pathWithinApplication())
                || APP_REFRESH.matches(exchange.getRequest().getPath().pathWithinApplication())
                || APP_LOGOUT.matches(exchange.getRequest().getPath().pathWithinApplication())
                || APP_REGISTER.matches(exchange.getRequest().getPath().pathWithinApplication())
                || APP_RESET_PASSWORD.matches(exchange.getRequest().getPath().pathWithinApplication())
                || APP_SEND_CODE.matches(exchange.getRequest().getPath().pathWithinApplication()))) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            if (remote == null || remote.getAddress() == null) {
                    return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, 0, "暂时无法确认请求来源");
            }
            // 不读取客户端可伪造的Forwarded/X-Forwarded-For，也不解析包含密码的请求体。
            String key = keyPrefix + SecureUtil.sha256(remote.getAddress().getHostAddress());
            long localWait = acquireLocal(key);
            if (localWait > 0) {
                return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, localWait, "认证请求过于频繁，请稍后重试");
            }
            return acquireRedis(key).flatMap(waitMillis -> waitMillis > 0
                    ? reject(exchange, HttpStatus.TOO_MANY_REQUESTS, waitMillis, "认证请求过于频繁，请稍后重试")
                    : chain.filter(exchange));
        });
    }

    private Mono<Long> acquireRedis(String key) {
        return Mono.defer(() -> redisTemplate.execute(script, Collections.singletonList(key),
                        Arrays.asList(Integer.toString(maxRequests), Long.toString(windowMillis))).single())
                .timeout(redisTimeout)
                .map(waitMillis -> {
                    if (waitMillis < 0 || waitMillis > windowMillis) {
                        throw new IllegalStateException("限额存储返回无效结果");
                    }
                    if (redisUnavailable.getAndSet(false)) {
                        log.info("登录来源限额Redis访问已恢复");
                    }
                    return waitMillis;
                })
                .onErrorResume(ex -> {
                    if (!redisUnavailable.getAndSet(true)) {
                        log.warn("登录来源限额Redis暂不可用，使用已执行的本地限额");
                    }
                    // 本地准入已经计数；不重试Redis或登录，超时不代表脚本一定未执行。
                    return Mono.just(0L);
                });
    }

    private long acquireLocal(String key) {
        synchronized (localWindows) {
            long now = System.nanoTime();
            Iterator<LocalWindow> iterator = localWindows.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().deadlineNanos - now > 0) {
                    break;
                }
                iterator.remove();
            }
            LocalWindow window = localWindows.get(key);
            if (window == null) {
                if (localWindows.size() >= maxLocalSources) {
                    return remainingMillis(localWindows.values().iterator().next().deadlineNanos, now);
                }
                window = new LocalWindow(now + windowNanos);
                localWindows.put(key, window);
            }
            if (window.requests >= maxRequests) {
                return remainingMillis(window.deadlineNanos, now);
            }
            window.requests++;
            return 0;
        }
    }

    private long remainingMillis(long deadlineNanos, long now) {
        return Math.max(1, (deadlineNanos - now + 999999) / 1000000);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, long waitMillis, String message) {
        if (waitMillis > 0) {
            exchange.getResponse().getHeaders().set(
                    HttpHeaders.RETRY_AFTER, Long.toString((waitMillis + 999) / 1000));
        }
        return GatewayResponseUtils.writeError(exchange, objectMapper, status, message);
    }

    private static final class LocalWindow {
        private final long deadlineNanos;
        private int requests;

        private LocalWindow(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }
    }
}
