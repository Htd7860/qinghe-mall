package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;

/** 按用户类型检查真实账号来源，不接收客户端自报身份作为可信结果。 */
public interface OAuth2UserEligibilityChecker {

    UserTypeEnum getUserType();

    /** 检查可信 Token 指向的真实账号和租户归属，失败时抛异常。 */
    void check(Long userId, Long tenantId);

    /** 可携带已由持久凭据提供的身份快照，由真实账号策略检查会话代次。 */
    default void check(Long userId, Long tenantId, java.util.Map<String, String> userInfo) {
        throw new IllegalStateException("该用户类型尚未实现凭据版本检查");
    }
}
