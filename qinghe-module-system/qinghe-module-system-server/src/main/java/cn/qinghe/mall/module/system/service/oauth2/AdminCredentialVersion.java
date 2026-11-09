package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import java.util.Map;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 持久账号状态每次读取，故Redis旧快照不能让失效凭据重新生效。 */
public final class AdminCredentialVersion {
    public static final String INFO_KEY = "credentialVersion";
    private AdminCredentialVersion() { }

    public static Long current(AdminUserDO user) {
        if (user == null || user.getCredentialVersion() == null || user.getCredentialVersion() < 0) {
            throw new IllegalStateException("账号凭据版本数据异常");
        }
        return user.getCredentialVersion();
    }

    public static void requireMatches(AdminUserDO user, Map<String, String> tokenInfo) {
        // 老access未记录代次时失败关闭，请重新登录；不猜测为当前版本。
        if (tokenInfo == null || !current(user).toString().equals(tokenInfo.get(INFO_KEY))) {
            throw exception0(401, "账号凭据已失效，请重新登录");
        }
    }
}
