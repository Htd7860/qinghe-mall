package cn.qinghe.mall.module.system.dal.redis.oauth2;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.system.dal.redis.RedisKeyConstants;
import cn.qinghe.mall.module.system.dal.redis.oauth2.dto.OAuth2AccessTokenCacheDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.redis.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2AccessTokenRedisDAO
 * @Date：2026/10/23 21:55
 * @Filename：OAuth2AccessTokenRedisDAO
 *
 * Token 校验快照的缓存访问。数据库查询、撤销事务由既有Service负责。
 * 本批方法由助手按用户授权补齐（DEC-043），保留用户的类及构造注入结构。
 */
@Repository
public class OAuth2AccessTokenRedisDAO {

    private static final long SNAPSHOT_MAX_AGE_SECONDS = 5;
    private static final int MAX_ACCESS_TOKEN_LENGTH = 128;

    private final StringRedisTemplate stringRedisTemplate;
    private final String keyPrefix;

    public OAuth2AccessTokenRedisDAO(
            StringRedisTemplate stringRedisTemplate,
            @Value("${qinghe.redis.key-prefix}") String keyPrefix) {
        // 配置错误在创建 Bean 时暴露，避免不同环境意外共用同一批 Key。
        if (keyPrefix == null || !keyPrefix.matches("[A-Za-z0-9:_-]+")
                || keyPrefix.endsWith(":")) {
            throw new IllegalArgumentException("Redis Key 前缀配置不合法");
        }
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "Redis 模板不能为空");
        this.keyPrefix = keyPrefix;
    }

    /**
     * 读取快照。未命中或快照已失效时返回 null，由 Service 决定是否查询数据库。
     * Redis 故障、损坏 JSON 和缺字段不伪装成普通未命中，异常交给上层处理。
     */
    public OAuth2AccessTokenCacheDTO get(String accessToken) {
        String redisKey = formatKey(accessToken);
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return null;
        }

        OAuth2AccessTokenCacheDTO snapshot = JsonUtils.parseObject(json, OAuth2AccessTokenCacheDTO.class);
        // 不能只依赖 Redis TTL：读到值后，反序列化期间也可能刚好超过有效窗口。
        if (remainingTtl(snapshot, LocalDateTime.now()).toMillis() <= 0) {
            return null;
        }
        return snapshot;
    }

    /**
     * 写入有效快照。checkedAt 必须由 Service 在开始权威查询前记录，不能在此刷新。
     */
    public void set(String accessToken, OAuth2AccessTokenCacheDTO snapshot) {
        String redisKey = formatKey(accessToken);
        if (remainingTtl(snapshot, LocalDateTime.now()).toMillis() <= 0) {
            return;
        }

        String json = JsonUtils.toJsonString(snapshot);
        // 序列化也会耗时，所以在发送前再计算一次剩余时间。
        Duration ttl = remainingTtl(snapshot, LocalDateTime.now());
        if (ttl.toMillis() <= 0) {
            return;
        }
        // 一次 SET 同时附带 TTL，避免 SET 成功而单独 EXPIRE 失败留下永久 Key。
        stringRedisTemplate.opsForValue().set(redisKey, json, ttl);
    }

    /**
     * 仅删除缓存，Key 不存在也可调用。完整退出还需要 Service 撤销数据库凭据。
     */
    public void delete(String accessToken) {
        String redisKey = formatKey(accessToken);
        stringRedisTemplate.delete(redisKey);
    }

    /**
     * Key = 环境前缀 + 业务前缀 + Token 摘要，不将原始 Token 暴露在 Key 中。
     */
    private String formatKey(String accessToken) {
        if (!StringUtils.hasText(accessToken) || accessToken.length() > MAX_ACCESS_TOKEN_LENGTH) {
            throw new IllegalArgumentException("访问令牌格式不合法");
        }
        for (int i = 0; i < accessToken.length(); i++) {
            char character = accessToken.charAt(i);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)
                    || Character.isISOControl(character) || character == ',') {
                throw new IllegalArgumentException("访问令牌格式不合法");
            }
        }
        String digest = DigestUtil.sha256Hex(accessToken);
        return keyPrefix + ":" + String.format(RedisKeyConstants.OAUTH2_ACCESS_TOKEN, digest);
    }

    /**
     * 返回 min(Token 到期时间, checkedAt + 5 秒) 与 now 的差值。
     * 已撤销或到期返回零；无效结构直接报错，不把它当成可信身份。
     */
    private Duration remainingTtl(OAuth2AccessTokenCacheDTO snapshot, LocalDateTime now) {
        validateSnapshot(snapshot);
        if (snapshot.getCheckedAt().isAfter(now)) {
            throw new IllegalStateException("Token 缓存检查时间晚于当前时间");
        }
        if (snapshot.getRevokedTime() != null) {
            return Duration.ZERO;
        }

        LocalDateTime deadline = snapshot.getCheckedAt().plusSeconds(SNAPSHOT_MAX_AGE_SECONDS);
        if (snapshot.getExpiresTime().isBefore(deadline)) {
            deadline = snapshot.getExpiresTime();
        }
        if (!deadline.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, deadline);
    }

    private void validateSnapshot(OAuth2AccessTokenCacheDTO snapshot) {
        if (snapshot == null
                || snapshot.getUserId() == null || snapshot.getUserId() <= 0
                || snapshot.getUserType() == null || UserTypeEnum.valueOf(snapshot.getUserType()) == null
                || snapshot.getTenantId() == null || snapshot.getTenantId() < 0
                || !StringUtils.hasText(snapshot.getClientId())
                || snapshot.getUserInfo() == null || snapshot.getScopes() == null
                || snapshot.getExpiresTime() == null || snapshot.getCheckedAt() == null) {
            throw new IllegalStateException("Token 缓存快照字段不合法");
        }
    }
}
