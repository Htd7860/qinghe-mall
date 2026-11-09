package cn.qinghe.mall.module.system.service.auth;

import cn.qinghe.mall.module.system.framework.security.LoginFailureLimitProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 保留原后台Bean与构造契约，算法直接复用Redis模块，不重置现有Key空间。 */
@Service
public class LoginFailureLimiter extends cn.qinghe.mall.framework.redis.core.ratelimit.LoginFailureLimiter {
    public LoginFailureLimiter(StringRedisTemplate redisTemplate,
            @Value("${qinghe.redis.key-prefix}") String keyPrefix,LoginFailureLimitProperties properties) {
        super(redisTemplate,keyPrefix,properties.getMaxFailures(),properties.getWindowSeconds(),properties.getMaxLocalSubjects());
    }
}
