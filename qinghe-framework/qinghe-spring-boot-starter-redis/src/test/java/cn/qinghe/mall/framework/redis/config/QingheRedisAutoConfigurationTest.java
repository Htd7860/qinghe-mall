package cn.qinghe.mall.framework.redis.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Real Spring bean registration with test doubles for clients: no Redis connection. */
class QingheRedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QingheRedisAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void importsShouldDeclareTheRedisConfiguration() {
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .forEach(candidates::add);

        assertThat(candidates).contains(QingheRedisAutoConfiguration.class.getName());
    }

    @Test
    void defaultTemplateShouldUseExistingFactoryAndConfiguredSerializers() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RedisTemplate.class);
            RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
            RedisConnectionFactory factory = context.getBean(RedisConnectionFactory.class);

            assertThat(template.getConnectionFactory()).isSameAs(factory);
            assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(template.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
            assertThat(template.getHashValueSerializer()).isSameAs(template.getValueSerializer());
            verifyNoInteractions(factory);
        });
    }

    @Test
    void applicationTemplateShouldReplaceTheDefaultWithoutBeanOverriding() {
        runner.withUserConfiguration(ApplicationTemplateConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RedisTemplate.class);
            RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);

            assertThat(template.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(template.getHashValueSerializer()).isInstanceOf(StringRedisSerializer.class);
            verifyNoInteractions(context.getBean(RedisConnectionFactory.class));
        });
    }

    @Test
    void actualRedissonAndBootDefaultsShouldBackOffWhileStringTemplateRemainsUsable() {
        // Reverse declaration order deliberately: Boot sorts actual auto-configurations.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RedisAutoConfiguration.class,
                        RedissonAutoConfigurationV2.class,
                        QingheRedisAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // StringRedisTemplate is itself a RedisTemplate subclass, so expect two here.
                    assertThat(context.getBeanNamesForType(RedisTemplate.class))
                            .containsExactlyInAnyOrder("redisTemplate", "stringRedisTemplate");
                    RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
                    assertThat(template.getValueSerializer())
                            .isInstanceOf(GenericJackson2JsonRedisSerializer.class);

                    StringRedisTemplate strings = context.getBean(StringRedisTemplate.class);
                    assertThat(strings.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
                    assertThat(strings.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
                    assertThat(strings.getHashValueSerializer()).isInstanceOf(StringRedisSerializer.class);
                    verifyNoInteractions(context.getBean(RedisConnectionFactory.class));
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ApplicationTemplateConfiguration {
        @Bean(name = "redisTemplate")
        RedisTemplate<String, Object> applicationRedisTemplate(RedisConnectionFactory factory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);
            StringRedisSerializer strings = new StringRedisSerializer();
            template.setKeySerializer(strings);
            template.setHashKeySerializer(strings);
            template.setValueSerializer(strings);
            template.setHashValueSerializer(strings);
            return template;
        }
    }
}
