package cn.qinghe.mall.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 登录来源请求限额，区别于system中的账号失败次数限制。 */
@Validated
@ConfigurationProperties(prefix = "qinghe.gateway.login-rate-limit")
public class LoginRateLimitProperties {

    @Min(1)
    @Max(10000)
    private int maxRequests = 60;

    @Min(1)
    @Max(3600)
    private long windowSeconds = 60;

    @Min(1)
    @Max(100000)
    private int maxLocalSources = 10000;

    @Min(50)
    @Max(1000)
    private long redisTimeoutMillis = 200;

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9:_-]{1,100}")
    private String keyPrefix = "qinghe:local";

    public int getMaxRequests() { return maxRequests; }
    public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }
    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    public int getMaxLocalSources() { return maxLocalSources; }
    public void setMaxLocalSources(int maxLocalSources) { this.maxLocalSources = maxLocalSources; }
    public long getRedisTimeoutMillis() { return redisTimeoutMillis; }
    public void setRedisTimeoutMillis(long redisTimeoutMillis) { this.redisTimeoutMillis = redisTimeoutMillis; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
}
