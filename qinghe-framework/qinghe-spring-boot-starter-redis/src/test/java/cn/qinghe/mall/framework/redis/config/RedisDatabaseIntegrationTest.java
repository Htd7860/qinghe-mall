package cn.qinghe.mall.framework.redis.config;

import cn.qinghe.mall.framework.redis.config.RedisSerializationTest.CacheSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User-run integration tests against local Redis DB 15 only.
 * Explicit opt-in, empty database precondition, and cleanup of this run's exact keys only.
 */
@EnabledIfEnvironmentVariable(named = "QINGHE_REDIS_TEST_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(20)
class RedisDatabaseIntegrationTest {

    private static final int TEST_DATABASE = 15;
    private static final Duration SAFETY_TTL = Duration.ofSeconds(30);
    private static final LocalDateTime EXPIRY =
            LocalDateTime.of(2026, 10, 23, 19, 30, 45, 123456789);

    private final String runId = UUID.randomUUID().toString();
    private final String clientName = "qinghe-redis-test-" + runId;
    private final String keyPrefix = "qinghe:test:redis:" + runId + ":";
    private final Set<String> ownedKeys = new LinkedHashSet<>();

    private RedissonClient client;
    private RedisTemplate<String, Object> template;
    private StringRedisTemplate strings;

    @BeforeAll
    void connectToDedicatedEmptyDatabase() throws Exception {
        Config config = new Config();
        config.setThreads(2);
        config.setNettyThreads(2);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setDatabase(TEST_DATABASE)
                .setClientName(clientName)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(0)
                .setConnectionPoolSize(2)
                .setConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(1)
                .setSubscriptionConnectionMinimumIdleSize(1);

        client = Redisson.create(config);
        try {
            RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
            factory.afterPropertiesSet();

            // Read the server's selected DB before any key is written, not just local config.
            try (RedisConnection connection = factory.getConnection()) {
                // Some Redisson adapters return CLIENT LIST lines despite the declared generic type.
                List<?> rawClients = connection.getClientList();
                List<RedisClientInfo> ownConnections = new ArrayList<>();
                for (Object rawClient : rawClients) {
                    RedisClientInfo info = clientInfo(rawClient);
                    if (clientName.equals(info.getName())) {
                        ownConnections.add(info);
                    }
                }
                assertThat(ownConnections)
                        .as("Redis must expose this run's named connections before writing")
                        .isNotEmpty();
                for (RedisClientInfo info : ownConnections) {
                    assertThat(info.getDatabaseId())
                            .as("This test may only write Redis DB 15")
                            .isEqualTo((long) TEST_DATABASE);
                }
                assertThat(connection.dbSize())
                        .as("DB 15 must be reserved and empty; do not clear an occupied DB to run this test")
                        .isZero();
            }

            // No Spring container here: explicitly initialize the template returned by user config.
            template = new QingheRedisAutoConfiguration().redisTemplate(factory);
            template.afterPropertiesSet();
            strings = new StringRedisTemplate(factory);
        } catch (Exception | AssertionError failure) {
            client.shutdown(0, 3, TimeUnit.SECONDS);
            client = null;
            throw failure;
        }
    }

    @AfterEach
    void removeOnlyExactKeysCreatedByThisRun() {
        if (template == null || ownedKeys.isEmpty()) {
            return;
        }
        for (String key : ownedKeys) {
            if (!key.startsWith(keyPrefix)) {
                throw new IllegalStateException("Refusing to clean a key outside this test run");
            }
        }
        template.delete(new ArrayList<>(ownedKeys));
        ownedKeys.clear();
    }

    @AfterAll
    void closeClient() {
        try {
            removeOnlyExactKeysCreatedByThisRun();
        } finally {
            if (client != null) {
                client.shutdown(0, 3, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void valueShouldRoundTripThroughRedisWithReadableKeyAndJson() {
        String key = ownedKey("value");
        CacheSnapshot original = new CacheSnapshot(101L, "青禾商品", EXPIRY);

        template.opsForValue().set(key, original, SAFETY_TTL);

        assertSnapshot(template.opsForValue().get(key), original);
        // A plain StringRedisTemplate must locate the exact same key and see JSON bytes.
        String rawJson = strings.opsForValue().get(key);
        assertThat(rawJson).contains("\"@class\"", "青禾商品", "2026-10-23T19:30:45.123456789");
        assertThat(template.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    void hashShouldUseStringFieldsAndRoundTripJsonValues() {
        String key = ownedKey("hash");
        String field = "snapshot";
        CacheSnapshot original = new CacheSnapshot(202L, "会员商品", EXPIRY);

        template.opsForHash().put(key, field, original);
        assertThat(template.expire(key, SAFETY_TTL)).isTrue();

        assertSnapshot(template.opsForHash().get(key, field), original);
        Object rawJson = strings.opsForHash().get(key, field);
        assertThat(rawJson).isInstanceOf(String.class);
        assertThat((String) rawJson).contains("\"@class\"", "会员商品");
        assertThat(template.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    void ttlShouldBePositiveThenExpireOnTheServer() throws InterruptedException {
        String key = ownedKey("expiry");
        Duration ttl = Duration.ofSeconds(5);
        template.opsForValue().set(key, "temporary-value", ttl);

        assertThat(template.opsForValue().get(key)).isEqualTo("temporary-value");
        Long remaining = template.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(remaining).isBetween(1L, ttl.toMillis());

        // Poll the observable server result, with a bound rather than one exact sleep interval.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (Boolean.TRUE.equals(template.hasKey(key)) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(template.hasKey(key)).isFalse();
        assertThat(template.opsForValue().get(key)).isNull();
    }

    @Test
    void deletingAValueShouldRemoveOnlyThatExactKey() {
        String removedKey = ownedKey("delete");
        String retainedKey = ownedKey("retain");
        template.opsForValue().set(removedKey, "remove-me", SAFETY_TTL);
        template.opsForValue().set(retainedKey, "keep-me", SAFETY_TTL);

        assertThat(template.delete(removedKey)).isTrue();
        assertThat(template.opsForValue().get(removedKey)).isNull();
        assertThat(template.opsForValue().get(retainedKey)).isEqualTo("keep-me");
        assertThat(template.delete(removedKey)).isFalse();
    }

    @Test
    void stringTemplateShouldKeepPlainTextWithoutJsonQuotes() {
        String key = ownedKey("plain-string");
        String original = "青禾 plain text";
        strings.opsForValue().set(key, original, SAFETY_TTL);

        assertThat(strings.opsForValue().get(key)).isEqualTo(original);
        assertThat(strings.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
    }

    private String ownedKey(String suffix) {
        String key = keyPrefix + suffix;
        ownedKeys.add(key);
        return key;
    }

    private RedisClientInfo clientInfo(Object rawClient) {
        if (rawClient instanceof RedisClientInfo) {
            return (RedisClientInfo) rawClient;
        }
        if (!(rawClient instanceof String)) {
            throw new IllegalStateException("Unsupported CLIENT LIST result; refusing to write test keys");
        }
        Properties properties = new Properties();
        for (String token : ((String) rawClient).split("\\s+")) {
            int separator = token.indexOf('=');
            if (separator > 0) {
                properties.setProperty(token.substring(0, separator), token.substring(separator + 1));
            }
        }
        return new RedisClientInfo(properties);
    }

    private void assertSnapshot(Object restored, CacheSnapshot original) {
        assertThat(restored).isInstanceOf(CacheSnapshot.class);
        CacheSnapshot snapshot = (CacheSnapshot) restored;
        assertThat(snapshot.getId()).isEqualTo(original.getId());
        assertThat(snapshot.getName()).isEqualTo(original.getName());
        assertThat(snapshot.getExpiresTime()).isEqualTo(original.getExpiresTime());
    }
}
