package cn.qinghe.mall.framework.redis.core.ratelimit;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;

/** 复用当前已实现的账号失败限额；不自动注册Bean，不用于刷新/退出/Token校验。 */
public class LoginFailureLimiter {
    private static final Logger log=LoggerFactory.getLogger(LoginFailureLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;
    private final String keyPrefix;
    private final int maxFailures;
    private final int maxLocalSubjects;
    private final long windowMillis;
    private final long windowNanos;
    private final Map<String, LocalState> localStates = new HashMap<>();
    private final AtomicBoolean redisUnavailable = new AtomicBoolean();

    public LoginFailureLimiter(StringRedisTemplate redisTemplate,String keyPrefix,
            int maxFailures,long windowSeconds,int maxLocalSubjects) {
        if (maxFailures<1 || maxFailures>100 || windowSeconds<60 || windowSeconds>86400
                || maxLocalSubjects<1 || maxLocalSubjects>100000) {
            throw new IllegalArgumentException("登录限额配置超出范围");
        }
        if (keyPrefix == null || !keyPrefix.matches("[A-Za-z0-9:_-]+") || keyPrefix.endsWith(":")) {
            throw new IllegalArgumentException("Redis Key前缀配置不合法");
        }
        this.redisTemplate = Objects.requireNonNull(redisTemplate,"Redis客户端不能为空");
        this.keyPrefix = keyPrefix;
        this.maxFailures = maxFailures;
        this.maxLocalSubjects = maxLocalSubjects;
        this.windowMillis = TimeUnit.SECONDS.toMillis(windowSeconds);
        this.windowNanos = TimeUnit.SECONDS.toNanos(windowSeconds);
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("redis/login-failure-limit.lua"));
        this.script.setResultType(Long.class);
    }

    public <T> T execute(String clientId, String tenantCode, String username, Supplier<T> action) {
        Objects.requireNonNull(action,"登录动作不能为空");
        if (clientId==null || !clientId.matches("[A-Za-z0-9:_-]{1,64}")
                || tenantCode!=null && tenantCode.length()>64 || username==null || username.isEmpty() || username.length()>64) {
            throw new IllegalArgumentException("登录限额主体格式不正确");
        }
        String subject = clientId + "\n" + (tenantCode == null ? "" : tenantCode)
                + "\n" + username.toLowerCase(Locale.ROOT);
        String key = keyPrefix + ":" + String.format(
                "system:auth:login-failure:%s", DigestUtil.sha256Hex(subject));
        String ticket = "attempt:" + UUID.randomUUID();

        // 正常时也保留有界本地影子，Redis中断后不从完全空白的失败记录开始。
        acquireLocal(key, ticket);
        boolean redisAcquired = false;
        try {
            Long retryMillis = runRedis("ACQUIRE", key, ticket);
            if (retryMillis != null && retryMillis > 0) {
                throw new LimitExceededException(ceilSeconds(retryMillis, 1000));
            }
            redisAcquired = retryMillis != null;
            if (!redisAcquired) {
                checkLocalFailureLimit(key, ticket);
            }
            T result = action.get();
            finish("SUCCESS", key, ticket, redisAcquired);
            return result;
        } catch (ServiceException ex) {
            finish(UNAUTHORIZED.getCode().equals(ex.getCode()) ? "FAILURE" : "RELEASE",
                    key, ticket, redisAcquired);
            throw ex;
        } catch (RuntimeException ex) {
            finish("RELEASE", key, ticket, redisAcquired);
            throw ex;
        }
    }

    private Long runRedis(String action, String key, String ticket) {
        try {
            Long result = redisTemplate.execute(script, Collections.singletonList(key),
                    action, ticket, Integer.toString(maxFailures), Long.toString(windowMillis));
            if (result == null || result < 0) {
                throw new IllegalStateException("登录限额存储响应异常");
            }
            if (redisUnavailable.compareAndSet(true, false)) {
                log.info("Login limit storage recovered");
            }
            return result;
        } catch (RuntimeException ex) {
            // 不把Redis异常、Key或原始账号/密码输出到日志；本地准入仍执行。
            if (redisUnavailable.compareAndSet(false, true)) {
                log.warn("Login limit storage unavailable; bounded local protection active");
            }
            return null;
        }
    }

    private void acquireLocal(String key, String ticket) {
        synchronized (localStates) {
            long now = System.nanoTime();
            LocalState state = localStates.get(key);
            if (state != null && state.deadline - now <= 0) {
                localStates.remove(key);
                state = null;
            }
            if (state == null) {
                if (localStates.size() >= maxLocalSubjects) {
                    localStates.values().removeIf(value -> value.deadline - now <= 0);
                }
                if (localStates.size() >= maxLocalSubjects) {
                    long remaining = localStates.values().stream()
                            .mapToLong(value -> value.deadline - now).min().orElse(windowNanos);
                    throw new LimitExceededException(ceilSeconds(remaining, 1_000_000_000));
                }
                state = new LocalState(now + windowNanos);
                localStates.put(key, state);
            }
            // 正常时失败数以Redis为准，避免其他实例成功清零后仍被本地旧影子拦住。
            if (state.pending.size() >= maxFailures) {
                throw new LimitExceededException(ceilSeconds(state.deadline - now, 1_000_000_000));
            }
            state.pending.add(ticket);
        }
    }

    private void checkLocalFailureLimit(String key, String ticket) {
        synchronized (localStates) {
            LocalState state = localStates.get(key);
            if (state == null || !state.pending.contains(ticket)
                    || state.deadline - System.nanoTime() <= 0) {
                throw new LimitExceededException(1);
            }
            if (state.failures + state.pending.size() > maxFailures) {
                throw new LimitExceededException(
                        ceilSeconds(state.deadline - System.nanoTime(), 1_000_000_000));
            }
        }
    }

    private void finish(String outcome, String key, String ticket, boolean redisAcquired) {
        synchronized (localStates) {
            LocalState state = localStates.get(key);
            if (state != null && state.deadline - System.nanoTime() <= 0) {
                localStates.remove(key);
            } else if (state != null && state.pending.remove(ticket)) {
                if ("FAILURE".equals(outcome)) {
                    state.failures = Math.min(maxFailures, state.failures + 1);
                } else if ("SUCCESS".equals(outcome)) {
                    state.failures = 0;
                }
                if (state.failures == 0 && state.pending.isEmpty()) {
                    localStates.remove(key);
                }
            }
        }
        if (redisAcquired) {
            // 计数存储故障不改写密码/数据库操作的原结果，已知占位由TTL兜底释放。
            runRedis(outcome, key, ticket);
        }
    }

    private static long ceilSeconds(long remaining, long unitsPerSecond) {
        return Math.max(1, (Math.max(1, remaining) - 1) / unitsPerSecond + 1);
    }

    private static final class LocalState {
        private final long deadline;
        private final Set<String> pending = new HashSet<>();
        private int failures;

        private LocalState(long deadline) {
            this.deadline = deadline;
        }
    }

    public static final class LimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        private LimitExceededException(long retryAfterSeconds) {
            super("登录尝试过于频繁，请稍后重试");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
