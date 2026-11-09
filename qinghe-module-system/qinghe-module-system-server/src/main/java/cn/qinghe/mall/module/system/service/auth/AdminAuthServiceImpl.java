package cn.qinghe.mall.module.system.service.auth;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.service.oauth2.AdminTokenIssueService;
import cn.qinghe.mall.module.system.service.oauth2.OAuth2TokenRevokeService;
import cn.qinghe.mall.module.system.service.oauth2.dto.OAuth2TokenPairDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.user.AdminUserService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserService userService;
    private final TenantService tenantService;
    private final AdminTokenIssueService tokenIssueService;
    private final OAuth2TokenRevokeService tokenRevokeService;

    public AdminAuthServiceImpl(AdminUserService userService,
                                TenantService tenantService,
                                AdminTokenIssueService tokenIssueService,
                                OAuth2TokenRevokeService tokenRevokeService) {
        this.userService = userService;
        this.tenantService = tenantService;
        this.tokenIssueService = tokenIssueService;
        this.tokenRevokeService = tokenRevokeService;
    }

    @Override
    public OAuth2TokenPairDTO login(String username, String password, Long tenantId,
                                    String clientId, List<String> scopes) {
        // 密码校验放在签发事务外，通过另一个Bean的代理进入独立签发事务。
        AdminUserDO user = authenticate(username, password, tenantId);
        try {
            return tokenIssueService.createToken(user, clientId, scopes);
        } catch (ServiceException ex) {
            if (GlobalErrorCodeConstants.UNAUTHORIZED.getCode().equals(ex.getCode())) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            throw ex;
        }
    }

    @Override
    public OAuth2TokenPairDTO refreshToken(String refreshToken, String clientId) {
        // 经另一个Bean的代理进入刷新事务，资格检查和失败分类由签发服务负责。
        return tokenIssueService.refreshAccessToken(refreshToken, clientId);
    }

    @Override
    public void logout(String accessToken, String clientId) {
        // 通过独立Bean代理进入撤销事务；提交后缓存清理的失败也须向调用方传递。
        tokenRevokeService.revokeAccessToken(accessToken, clientId);
    }

    @Override
    public AdminUserDO authenticate(String username, String password, Long tenantId) {
        if (tenantId == null || tenantId < 0) {
            throw new IllegalStateException("登录目标租户尚未确定");
        }
        if (username == null || !username.matches("[A-Za-z0-9]{4,30}")
                || password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }

        AdminUserDO user = userService.getUserByUsername(username, tenantId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (user.getId() == null || user.getId() <= 0
                || !username.equals(user.getUsername())
                || !tenantId.equals(user.getTenantId())) {
            throw new IllegalStateException("登录账号身份数据异常");
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }

        try {
            AdminUserDO current = userService.validateUserForAuthentication(
                    user.getId(), tenantId);
            // 密码验证期间发生改密，本次结果不能继续用于登录。
            if (!Objects.equals(user.getPassword(), current.getPassword())) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            if (tenantId > 0) {
                tenantService.validateForAuthentication(tenantId);
            }
            return current;
        } catch (ServiceException ex) {
            // 已知身份失败统一提示，基础设施或配置故障继续向上传递。
            if (GlobalErrorCodeConstants.UNAUTHORIZED.getCode().equals(ex.getCode())) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            throw ex;
        }
    }
}
