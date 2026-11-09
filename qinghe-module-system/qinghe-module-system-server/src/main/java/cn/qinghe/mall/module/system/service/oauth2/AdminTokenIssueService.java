package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;

import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：AdminTokenIssueService
 * @Date：2026/10/25 17:58
 * @Filename：AdminTokenIssueService
 */
public interface AdminTokenIssueService {
    /** 仅接受内部刚完成密码认证的账号，不接受外部请求构造的账号对象。 */
    OAuth2TokenPairDTO createToken(
            AdminUserDO authenticatedUser,
            String clientId,
            List<String> scopes);

    /** 内部后台刷新；clientId由服务端入口确定，保留原refreshToken且不延长其有效期。 */
    OAuth2TokenPairDTO refreshAccessToken(String refreshToken, String clientId);
}
