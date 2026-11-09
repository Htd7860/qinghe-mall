package cn.qinghe.mall.module.system.service.oauth2.dto;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** 内部签发结果；不直接作为浏览器响应，也不在 toString 中输出凭据。 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class OAuth2TokenPairDTO {

    private String accessToken;

    private String refreshToken;

    private LocalDateTime expiresTime;

    private LocalDateTime refreshExpiresTime;
}
