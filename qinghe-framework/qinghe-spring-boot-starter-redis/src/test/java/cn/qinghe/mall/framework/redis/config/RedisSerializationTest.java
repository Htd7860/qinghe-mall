package cn.qinghe.mall.framework.redis.config;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Serialization tests only: no Spring application, Redis server or business tables. */
class RedisSerializationTest {

    private static final LocalDateTime EXPIRY =
            LocalDateTime.of(2026, 10, 23, 18, 30, 45, 123456789);

    @Test
    void modelShouldRetainItsTypeAndNanosecondExpiry() {
        CacheSnapshot original = new CacheSnapshot(101L, "青禾商品", EXPIRY);

        Object restored = roundTrip(original);

        assertThat(restored).isInstanceOf(CacheSnapshot.class).isNotSameAs(original);
        CacheSnapshot snapshot = (CacheSnapshot) restored;
        assertThat(snapshot.getId()).isEqualTo(original.getId());
        assertThat(snapshot.getName()).isEqualTo(original.getName());
        assertThat(snapshot.getExpiresTime()).isEqualTo(EXPIRY);
    }

    @Test
    void finalModelShouldAlsoRetainItsType() {
        FinalCacheSnapshot original = new FinalCacheSnapshot("final-model", EXPIRY);

        Object restored = roundTrip(original);

        assertThat(restored).isInstanceOf(FinalCacheSnapshot.class);
        FinalCacheSnapshot snapshot = (FinalCacheSnapshot) restored;
        assertThat(snapshot.getName()).isEqualTo("final-model");
        assertThat(snapshot.getExpiresTime()).isEqualTo(EXPIRY);
    }

    @Test
    void rootLocalDateTimeShouldNotBecomeAStringOrLosePrecision() {
        Object restored = roundTrip(EXPIRY);

        assertThat(restored).isInstanceOf(LocalDateTime.class).isEqualTo(EXPIRY);
    }

    @ParameterizedTest(name = "scalar {index}: {0}")
    @MethodSource("scalarValues")
    void scalarShouldRetainItsValueAndRuntimeType(Object original) {
        Object restored = roundTrip(original);

        assertThat(restored).isExactlyInstanceOf(original.getClass()).isEqualTo(original);
    }

    static Stream<Object> scalarValues() {
        return Stream.of(
                "青禾缓存", Boolean.TRUE, Integer.valueOf(7),
                Long.valueOf(9007199254740993L),
                new BigDecimal("12345.6700"),
                new BigInteger("123456789012345678901234567890"));
    }

    @Test
    void listShouldRetainElementTypesAndOrder() {
        List<CacheSnapshot> original = new ArrayList<>();
        original.add(new CacheSnapshot(101L, "first", EXPIRY));
        original.add(new CacheSnapshot(202L, "second", EXPIRY.plusMinutes(1)));

        Object restored = roundTrip(original);

        assertThat(restored).isInstanceOf(ArrayList.class);
        List<?> snapshots = (List<?>) restored;
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0)).isInstanceOf(CacheSnapshot.class);
        assertThat(snapshots.get(1)).isInstanceOf(CacheSnapshot.class);
        CacheSnapshot first = (CacheSnapshot) snapshots.get(0);
        CacheSnapshot second = (CacheSnapshot) snapshots.get(1);
        assertThat(first.getId()).isEqualTo(101L);
        assertThat(first.getName()).isEqualTo("first");
        assertThat(first.getExpiresTime()).isEqualTo(EXPIRY);
        assertThat(second.getId()).isEqualTo(202L);
        assertThat(second.getName()).isEqualTo("second");
        assertThat(second.getExpiresTime()).isEqualTo(EXPIRY.plusMinutes(1));
    }

    @Test
    void mapShouldRetainNestedMoneyTimeAndCollectionValues() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("id", 9007199254740993L);
        original.put("amount", new BigDecimal("19.90"));
        original.put("expiresTime", EXPIRY);
        original.put("labels", new ArrayList<>(Arrays.asList("new", "member")));

        Object restored = roundTrip(original);

        assertThat(restored).isInstanceOf(LinkedHashMap.class).isEqualTo(original);
        Map<?, ?> values = (Map<?, ?>) restored;
        assertThat(values.get("id")).isInstanceOf(Long.class);
        assertThat(values.get("amount")).isInstanceOf(BigDecimal.class);
        assertThat(((BigDecimal) values.get("amount")).scale()).isEqualTo(2);
        assertThat(values.get("expiresTime")).isInstanceOf(LocalDateTime.class);
        assertThat(values.get("labels")).isInstanceOf(ArrayList.class);
    }

    @Test
    void nullAndEmptyBytesShouldFollowSerializerNullContract() {
        RedisSerializer<Object> serializer = QingheRedisAutoConfiguration.buildRedisSerializer();

        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(null)).isNull();
        assertThat(serializer.deserialize(new byte[0])).isNull();
    }

    @Test
    void malformedJsonShouldFailInsteadOfBecomingAnEmptyValue() {
        RedisSerializer<Object> serializer = QingheRedisAutoConfiguration.buildRedisSerializer();

        assertThatThrownBy(() -> serializer.deserialize("{".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void typeOutsideConfiguredScopeShouldBeRejected() {
        RedisSerializer<Object> serializer = QingheRedisAutoConfiguration.buildRedisSerializer();
        // This is JSON input only; the test never creates or accesses a file.
        byte[] outsideType = "[\"java.io.File\",\"not-a-real-file\"]"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(outsideType))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseInstanceOf(InvalidTypeIdException.class)
                .hasMessageContaining("java.io.File");
    }

    private Object roundTrip(Object original) {
        // Separate instances ensure decoding does not depend on in-memory state of the writer.
        byte[] bytes = QingheRedisAutoConfiguration.buildRedisSerializer().serialize(original);
        return QingheRedisAutoConfiguration.buildRedisSerializer().deserialize(bytes);
    }

    /** Test-only model; public accessors permit ordinary Jackson bean binding. */
    public static class CacheSnapshot {
        private Long id;
        private String name;
        private LocalDateTime expiresTime;

        public CacheSnapshot() {
        }

        public CacheSnapshot(Long id, String name, LocalDateTime expiresTime) {
            this.id = id;
            this.name = name;
            this.expiresTime = expiresTime;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public LocalDateTime getExpiresTime() { return expiresTime; }
        public void setExpiresTime(LocalDateTime expiresTime) { this.expiresTime = expiresTime; }
    }

    public static final class FinalCacheSnapshot {
        private String name;
        private LocalDateTime expiresTime;

        public FinalCacheSnapshot() {
        }

        public FinalCacheSnapshot(String name, LocalDateTime expiresTime) {
            this.name = name;
            this.expiresTime = expiresTime;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public LocalDateTime getExpiresTime() { return expiresTime; }
        public void setExpiresTime(LocalDateTime expiresTime) { this.expiresTime = expiresTime; }
    }
}
