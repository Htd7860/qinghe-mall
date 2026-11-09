package cn.qinghe.mall.framework.redis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.redis.config
 * @Project：qinghe-mall
 * @name：QingheRedisAutoConfiguration
 * @Date：2026/10/23 18:06
 * @Filename：QingheRedisAutoConfiguration
 */
@AutoConfiguration(before= RedissonAutoConfigurationV2.class)
public class QingheRedisAutoConfiguration {

    /** 可选密码占位符缺失时绑定为空串；单机无密码连接不发送空AUTH，非空密码不改。 */
    @Bean
    public RedissonAutoConfigurationCustomizer optionalSingleServerPassword() {
        return config -> {
            if(config.isSingleConfig()) {
                // Redisson 4.7 starter把RedisProperties密码写到顶层Config，连接级配置也可能独立提供。
                if("".equals(config.getPassword()))config.setPassword(null);
                if("".equals(config.useSingleServer().getPassword()))config.useSingleServer().setPassword(null);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // 创建 RedisTemplate 对象
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 设置 RedisConnection 工厂。😈 它就是实现多种 Java Redis 客户端接入的秘密工厂。感兴趣的胖友，可以自己去撸下。
        template.setConnectionFactory(factory);
        // 使用 String 序列化方式，序列化 KEY 。
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        // 使用 JSON 序列化方式，序列化 VALUE
        RedisSerializer<?> redisSerializer = buildRedisSerializer();
        template.setValueSerializer(redisSerializer);
        template.setHashValueSerializer(redisSerializer);
        return template;
    }


    public static RedisSerializer<Object> buildRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        BasicPolymorphicTypeValidator validator =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("cn.qinghe.mall.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType(Number.class)
                        .build();

        mapper.activateDefaultTyping(
                validator,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
