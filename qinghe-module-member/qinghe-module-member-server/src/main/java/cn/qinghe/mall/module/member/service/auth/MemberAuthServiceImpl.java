package cn.qinghe.mall.module.member.service.auth;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.framework.auth.MemberTokenClient;
import cn.qinghe.mall.module.member.framework.auth.MemberTokenClientProperties;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原MemberAuthServiceImpl.login -> login0 -> createTokenAfterLoginSuccess。
 * 不移植社交绑定/短信自动开户；本批只接密码和远程签发，登录记录与公开保护随后接。
 * @author 芋道源码
 */
@Service
public class MemberAuthServiceImpl implements MemberAuthService {
    private final MemberPasswordAuthenticationService passwords;
    private final MemberTokenClient tokens;
    private final Semaphore calls;
    public MemberAuthServiceImpl(MemberPasswordAuthenticationService passwords,MemberTokenClient tokens,
            MemberTokenClientProperties settings) {
        this.passwords=passwords;this.tokens=tokens;this.calls=new Semaphore(settings.getMaxConcurrentCalls());
    }
    @Override
    public MemberTokenIssueRespDTO login(Long tenantId,String mobile,String password) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("会员密码登录不得持数据库事务调用远程签发");
        }
        if (tenantId==null || tenantId<=0 || TenantContextHolder.isIgnore()
                || TenantContextHolder.getTenantId()!=null && !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw exception0(403,"会员登录目标与店铺入口不匹配");
        }
        if (!calls.tryAcquire()) { throw exception0(503,"会员登录服务繁忙，请稍后重试"); }
        try {
            MemberUserDO user=passwords.authenticate(tenantId,mobile,password);
            return tokens.issue(user.getTenantId(),user.getId(),user.getCredentialVersion());
        } finally { calls.release(); }
    }
}
