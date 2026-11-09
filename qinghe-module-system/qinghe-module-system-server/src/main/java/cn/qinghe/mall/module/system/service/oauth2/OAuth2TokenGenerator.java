package cn.qinghe.mall.module.system.service.oauth2;

import java.security.SecureRandom;
import java.util.Base64;

/** 随机凭据生成器；生成的是 Token 本身，不是签名。 */
public final class OAuth2TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OAuth2TokenGenerator() {
    }

    public static String generateAccessToken() {
        return "at_" + randomToken();
    }

    public static String generateRefreshToken() {
        return "rt_" + randomToken();
    }

    private static String randomToken() {
        // 每次独立生成 32 字节，再编码为不带填充的 Base64URL 字符串。
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
