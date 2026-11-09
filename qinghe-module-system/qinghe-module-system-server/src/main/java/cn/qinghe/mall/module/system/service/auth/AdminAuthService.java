package cn.qinghe.mall.module.system.service.auth;

import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;

import java.util.List;

/** 后台认证与会话操作，仅供内部服务调用。 */
public interface AdminAuthService {

    /**
     * 校验登录凭据及当前身份资格，尚不签发 Token。
     *
     * @param username 登录账号
     * @param password 原始密码
     * @param tenantId 已确定的目标租户，平台为 0，商家为正数
     * @return 当前账号记录，仅供内部后续流程使用，不能直接作为 HTTP 响应
     */
    AdminUserDO authenticate(String username, String password, Long tenantId);

    /** 内部后台登录；clientId/tenantId由已确定的应用入口提供。 */
    OAuth2TokenPairDTO login(String username, String password, Long tenantId,
                             String clientId, List<String> scopes);

    /**
     * 内部后台刷新；clientId由服务端入口确定，身份和scope从刷新记录读取。
     * 无需再次提交密码，也不要求旧accessToken仍有效。
     */
    OAuth2TokenPairDTO refreshToken(String refreshToken, String clientId);

    /**
     * 内部退出，撤销访问令牌关联的整个会话，重复调用幂等。
     * clientId由服务端入口确定；不预先校验Token有效性，过期或受限会话也允许退出。
     * 浏览器主动携带Bearer；未来HTTP入口必须固定clientId并受控委托本方法。
     * 退出需允许定位过期或已撤销凭据，不能复用普通有效Token校验提前拒绝。
     */
    void logout(String accessToken, String clientId);
}
