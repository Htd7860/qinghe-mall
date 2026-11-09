package cn.qinghe.mall.module.system.dal.redis;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.redis
 * @Project：qinghe-mall
 * @name：RedisKeyConstants
 * @Date：2026/10/23 21:46
 * @Filename：RedisKeyConstants
 */
public interface RedisKeyConstants {
    String OAUTH2_ACCESS_TOKEN = "system:oauth2:access-token:%s";
    String AUTH_LOGIN_FAILURE = "system:auth:login-failure:%s";
}
