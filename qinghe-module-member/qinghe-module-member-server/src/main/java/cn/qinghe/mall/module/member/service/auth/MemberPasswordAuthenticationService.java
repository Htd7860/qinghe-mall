package cn.qinghe.mall.module.member.service.auth;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.user.MemberUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 择取yudao MemberAuthServiceImpl.login0：精确手机号、密码、账号启用检查。
 * 仅返回后端候选账号，调用者必须已权威解析店铺；没有公开HTTP或Token签发。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class MemberPasswordAuthenticationService {
    private final MemberUserMapper users;
    private final MemberPasswordService passwords;
    public MemberUserDO authenticate(Long tenantId,String mobile,String password) {
        if (tenantId==null || tenantId<=0) { throw new IllegalStateException("消费者登录目标尚未确定"); }
        if (mobile==null || !mobile.matches("^1[3-9][0-9]{9}$") || !passwords.validInput(password)) { throw rejected(); }
        return TenantUtils.execute(tenantId,() -> {
            MemberUserDO user=users.selectByMobile(mobile);
            if (user==null || Boolean.TRUE.equals(user.getDeleted())) { passwords.matchUnknown(password);throw rejected(); }
            requireIdentity(user,tenantId,mobile);
            if (!passwords.isPasswordMatch(password,user.getPassword())) { throw rejected(); }
            MemberUserDO current=users.selectCurrent(user.getId(),false);
            if (current==null || !Integer.valueOf(0).equals(current.getStatus()) || Boolean.TRUE.equals(current.getDeleted())) { throw rejected(); }
            requireIdentity(current,tenantId,mobile);
            if (!Objects.equals(user.getPassword(),current.getPassword()) || !Objects.equals(user.getCredentialVersion(),current.getCredentialVersion())) { throw rejected(); }
            return current;
        });
    }
    private void requireIdentity(MemberUserDO user,Long tenantId,String mobile) {
        if (user.getId()==null || user.getId()<=0 || !Objects.equals(tenantId,user.getTenantId()) || !Objects.equals(mobile,user.getMobile())
                || user.getCredentialVersion()==null || user.getCredentialVersion()<0 || user.getAuthVersion()==null || user.getAuthVersion()<0
                || !(Integer.valueOf(0).equals(user.getStatus()) || Integer.valueOf(1).equals(user.getStatus()))) {
            throw exception0(503,"会员账号配置异常");
        }
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException rejected() { return exception0(401,"手机号或密码错误，或账号当前不可用"); }
}
