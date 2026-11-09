package cn.qinghe.mall.module.system.service.oauth2;

import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2TokenEligibilityChecker
 * @Date：2026/10/24 10:59
 * @Filename：OAuth2TokenEligibilityChecker
 */


public interface OAuth2TokenEligibilityChecker {

    /**
     * 检查账号、租户、客户端及授权范围是否仍有效。
     * 不符合资格时抛出 code=401 的业务异常；
     * 查询故障时抛出服务异常。
     */
    void check(Long userId, Integer userType, Long tenantId,
               String clientId, List<String> scopes);

    default void check(Long userId, Integer userType, Long tenantId,
                       String clientId, List<String> scopes, java.util.Map<String, String> userInfo) {
        throw new IllegalStateException("凭据版本检查尚未接入");
    }
}
