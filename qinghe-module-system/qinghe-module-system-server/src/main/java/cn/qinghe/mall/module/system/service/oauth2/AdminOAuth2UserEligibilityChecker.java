package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.user.AdminUserService;
import org.springframework.stereotype.Service;

/** 平台后台人员及商家员工的真实账号资格检查。 */
@Service
public class AdminOAuth2UserEligibilityChecker
        implements OAuth2UserEligibilityChecker {

    private final AdminUserService adminUserService;
    private final TenantService tenantService;

    public AdminOAuth2UserEligibilityChecker(AdminUserService adminUserService,
                                            TenantService tenantService) {
        this.adminUserService = adminUserService;
        this.tenantService = tenantService;
    }

    @Override
    public UserTypeEnum getUserType() {
        return UserTypeEnum.ADMIN;
    }

    @Override
    public void check(Long userId, Long tenantId) {
        adminUserService.validateUserForAuthentication(userId, tenantId);

        // 平台账号归属为0，不伪造普通商家租户记录。
        if (tenantId > 0) {
            tenantService.validateForAuthentication(tenantId);
        }
    }

    @Override
    public void check(Long userId, Long tenantId, java.util.Map<String, String> userInfo) {
        AdminCredentialVersion.requireMatches(
                adminUserService.validateUserForAuthentication(userId, tenantId), userInfo);
        if (tenantId > 0) {
            tenantService.validateForAuthentication(tenantId);
        }
    }
}
