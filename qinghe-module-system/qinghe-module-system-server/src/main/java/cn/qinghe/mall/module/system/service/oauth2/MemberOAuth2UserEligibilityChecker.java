package cn.qinghe.mall.module.system.service.oauth2;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.module.system.framework.member.MemberQualificationClient;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 真实MEMBER策略，与ADMIN并列注册到既有EnumMap；不可拿员工账号或空检查兜底。 */
@Service
@RequiredArgsConstructor
public class MemberOAuth2UserEligibilityChecker implements OAuth2UserEligibilityChecker {
    private final MemberQualificationClient members;
    private final TenantService tenants;
    @Override public UserTypeEnum getUserType() { return UserTypeEnum.MEMBER; }
    @Override public void check(Long userId,Long tenantId) {
        requireTenant(tenantId);tenants.validateForAuthentication(tenantId);members.check(userId,tenantId,null);
    }
    @Override public void check(Long userId,Long tenantId,Map<String,String> userInfo) {
        requireTenant(tenantId);Long version=credentialVersion(userInfo);
        tenants.validateForAuthentication(tenantId);members.check(userId,tenantId,version);
    }
    private void requireTenant(Long tenantId) {
        if (tenantId==null || tenantId<=0) { throw exception0(401,"会员不能使用平台租户身份"); }
    }
    private Long credentialVersion(Map<String,String> info) {
        String value=info==null?null:info.get("credentialVersion");
        if (value==null || !value.matches("0|[1-9][0-9]{0,18}")) { throw exception0(401,"会员凭据缺少有效代次，请重新登录"); }
        try { return Long.valueOf(value); } catch (NumberFormatException error) { throw exception0(401,"会员凭据代次不正确"); }
    }
}
