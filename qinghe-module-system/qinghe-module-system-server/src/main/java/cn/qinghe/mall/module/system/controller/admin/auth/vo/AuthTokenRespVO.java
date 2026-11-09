package cn.qinghe.mall.module.system.controller.admin.auth.vo;

import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import lombok.Data;
import lombok.ToString;

import java.time.ZoneId;

/** 浏览器TokenPair；凭据不进入toString，时间使用epoch毫秒。 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class AuthTokenRespVO {

    private String accessToken;
    private String refreshToken;
    private Long expiresAt;
    private Long refreshExpiresAt;

    public static AuthTokenRespVO from(OAuth2TokenPairDTO pair) {
        // 内部签发使用LocalDateTime.now()，因此按相同JVM时区还原时间点。
        ZoneId zone = ZoneId.systemDefault();
        AuthTokenRespVO result = new AuthTokenRespVO();
        result.setAccessToken(pair.getAccessToken());
        result.setRefreshToken(pair.getRefreshToken());
        result.setExpiresAt(pair.getExpiresTime().atZone(zone).toInstant().toEpochMilli());
        result.setRefreshExpiresAt(pair.getRefreshExpiresTime().atZone(zone).toInstant().toEpochMilli());
        return result;
    }
}
