package cn.qinghe.mall.module.system.dal.redis.oauth2.dto;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.redis.oauth2.dto
 * @Project：qinghe-mall
 * @name：OAuth2AccessTokenCacheDTO
 * @Date：2026/10/23 21:40
 * @Filename：OAuth2AccessTokenCacheDTO
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class OAuth2AccessTokenCacheDTO {
    private Long userId;
    private Integer userType;
    private Long tenantId;
    private Map<String,String> userInfo;
    private String clientId;
    private List<String> scopes;
    private LocalDateTime expiresTime;
    private LocalDateTime revokedTime;
    private LocalDateTime checkedAt;
}
