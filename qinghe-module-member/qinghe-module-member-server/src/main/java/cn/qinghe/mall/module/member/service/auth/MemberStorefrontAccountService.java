package cn.qinghe.mall.module.member.service.auth;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.member.framework.auth.MemberStorefrontClient;
import cn.qinghe.mall.module.member.framework.sms.MemberSmsCodeProperties;
import cn.qinghe.mall.module.member.framework.sms.MemberSmsScene;
import cn.qinghe.mall.module.member.service.sms.MemberSmsCodeService;
import cn.qinghe.mall.module.member.service.user.MemberUserService;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原会员创建/找回/发送短信Service委托的品牌入口接线，不重复实现验证码或账号事务。
 * @author 芋道源码
 */
@Service
public class MemberStorefrontAccountService {
    private final MemberStorefrontClient storefronts;
    private final MemberUserService users;
    private final MemberSmsCodeService codes;
    private final Semaphore calls;
    public MemberStorefrontAccountService(MemberStorefrontClient storefronts,MemberUserService users,
            MemberSmsCodeService codes,MemberSmsCodeProperties settings) {
        this.storefronts=storefronts;this.users=users;this.codes=codes;
        // 覆盖新密码编码阶段，避免直到SMS消费时才限制并发。
        this.calls=new Semaphore(settings.getMaxConcurrent());
    }
    public boolean register(HttpServletRequest request,String store,String mobile,String password,String code) {
        return execute(request,store,true,entry -> {
            users.register(entry.getTenantId(),mobile,password,code,request.getRemoteAddr());
            return true; // 注册不自动登录，不暴露会员编号或Token。
        });
    }
    public boolean resetPassword(HttpServletRequest request,String store,String mobile,String password,String code) {
        return execute(request,store,false,entry -> {
            users.resetUserPassword(entry.getTenantId(),mobile,password,code);return true;
        });
    }
    public boolean sendCode(HttpServletRequest request,String store,String mobile,String purpose) {
        final MemberSmsScene scene;
        if ("register".equals(purpose)) { scene=MemberSmsScene.REGISTER; }
        else if ("reset-password".equals(purpose)) { scene=MemberSmsScene.RESET_PASSWORD; }
        else { throw exception0(400,"验证码用途不正确"); }
        return execute(request,store,scene==MemberSmsScene.REGISTER,entry -> {
            codes.sendSmsCode(entry.getTenantId(),mobile,scene);return true;
        });
    }
    private <T> T execute(HttpServletRequest request,String store,boolean registration,
            Function<MemberStorefrontRespDTO,T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("公开会员账号入口不得嵌套外层数据库事务");
        }
        if (!calls.tryAcquire()) { throw exception0(429,"账号服务繁忙，请稍后重试"); }
        try {
            MemberStorefrontRespDTO entry=storefronts.resolveRequest(request,store);
            if (registration && !Boolean.TRUE.equals(entry.getCanPurchase())) {
                throw exception0(403,"该商城暂不接受新会员注册");
            }
            T result=action.apply(entry);
            // 本地变更/短信受理可能已经完成，入口复查失败不声称回滚或自动重放。
            try {
                MemberStorefrontRespDTO current=storefronts.requireSameRequest(request,store,entry);
                if (registration && !Boolean.TRUE.equals(current.getCanPurchase())) {
                    throw exception0(403,"商城注册资格已变化");
                }
            }
            catch (RuntimeException changed) {
                throw exception0(503,"账号操作可能已完成，入口复核未确认，请勿重复提交");
            }
            return result;
        } catch (ServiceException known) { throw known; }
        catch (RuntimeException unknown) {
            // 底层SQL/短信异常可能含手机号、密码哈希或code，不保留cause进入全局日志。
            throw exception0(503,"账号操作结果未确认，请先查询登录状态");
        } finally { calls.release(); }
    }
}
