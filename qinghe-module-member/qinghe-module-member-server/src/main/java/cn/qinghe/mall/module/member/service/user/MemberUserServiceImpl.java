package cn.qinghe.mall.module.member.service.user;

import cn.hutool.core.util.RandomUtil;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.user.MemberUserMapper;
import cn.qinghe.mall.module.member.framework.sms.MemberSmsScene;
import cn.qinghe.mall.module.member.service.auth.MemberPasswordService;
import cn.qinghe.mall.module.member.service.sms.MemberSmsCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原MemberUserServiceImpl.createUser/resetUserPassword/encodePassword。
 * 本项目显式注册设置密码；验证码消费、本地账号变更与成功记录共用既有一次事务。
 * 不在此调用远程服务或签发Token，避免把本地提交描述为跨服务原子操作。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class MemberUserServiceImpl implements MemberUserService {
    private final MemberUserMapper users;
    private final MemberSmsCodeService smsCodes;
    private final MemberPasswordService passwords;

    @Override
    public Long register(Long tenantId,String mobile,String password,String code,String registerIp) {
        requireTarget(tenantId,mobile);
        requireIpLiteral(registerIp);
        // 密码编码在号码锁/数据库事务之外，减少持锁时间。
        String encoded=passwords.encodeNewPassword(password);
        try {
            return smsCodes.useSmsCode(tenantId,mobile,MemberSmsScene.REGISTER,code,() -> {
                MybatisUtils.requireWriteTransaction();
                if (users.lockIdentityByMobile(mobile)!=null) { throw unavailable(); }
                LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                MemberUserDO user=new MemberUserDO();
                user.setTenantId(tenantId);user.setMobile(mobile);user.setPassword(encoded);
                user.setStatus(0);user.setRegisterIp(registerIp);user.setRegisterTerminal(20); // 原TerminalEnum.H5
                user.setLoginIp("");user.setNickname("用户"+RandomUtil.randomNumbers(6));user.setAvatar("");
                user.setMarketingOptOut(false);user.setAuthVersion(0L);user.setCredentialVersion(0L);
                user.setCreator("sms-register");user.setUpdater("sms-register");user.setDeleted(false);
                user.setCreateTime(now);user.setUpdateTime(now);
                if (users.insert(user)!=1 || user.getId()==null || user.getId()<=0) {
                    throw new IllegalStateException("会员注册保存失败");
                }
                audit(user,"REGISTER",-1L,-1L);
                return user.getId();
            });
        } catch (DuplicateKeyException conflict) {
            // 包含逻辑删除身份的唯一手机号约束；不输出底层SQL及手机号。
            throw unavailable();
        }
    }

    @Override
    public void resetUserPassword(Long tenantId,String mobile,String password,String code) {
        requireTarget(tenantId,mobile);
        String encoded=passwords.encodeNewPassword(password);
        smsCodes.useSmsCode(tenantId,mobile,MemberSmsScene.RESET_PASSWORD,code,() -> {
            MybatisUtils.requireWriteTransaction();
            MemberUserDO user=users.lockIdentityByMobile(mobile);
            if (user==null || Boolean.TRUE.equals(user.getDeleted()) || !Integer.valueOf(0).equals(user.getStatus())) {
                throw unavailable();
            }
            requireCurrent(user,tenantId,mobile);
            Long auth=user.getAuthVersion(),credential=user.getCredentialVersion();
            if (auth==Long.MAX_VALUE || credential==Long.MAX_VALUE) {
                throw exception0(409,"会员凭据版本已达到上限，请联系管理员");
            }
            if (users.resetPassword(tenantId,user.getId(),encoded,auth,credential)!=1) {
                throw exception0(409,"会员账号已变化，请重新操作");
            }
            user.setAuthVersion(auth+1);user.setCredentialVersion(credential+1);
            audit(user,"PASSWORD_RESET",auth,credential);
            return null;
        });
    }

    private void audit(MemberUserDO user,String action,Long fromAuth,Long fromCredential) {
        if (users.auditAccount(user.getTenantId(),user.getId(),action,fromAuth,user.getAuthVersion(),
                fromCredential,user.getCredentialVersion())!=1) {
            throw new IllegalStateException("会员账号变更记录保存失败");
        }
    }
    private void requireCurrent(MemberUserDO user,Long tenantId,String mobile) {
        if (user.getId()==null || user.getId()<=0 || !Objects.equals(tenantId,user.getTenantId())
                || !Objects.equals(mobile,user.getMobile()) || user.getAuthVersion()==null || user.getAuthVersion()<0
                || user.getCredentialVersion()==null || user.getCredentialVersion()<0) {
            throw exception0(503,"会员账号配置异常");
        }
    }
    private void requireTarget(Long tenantId,String mobile) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("会员注册找回必须在外层事务之外调用");
        }
        if (tenantId==null || tenantId<=0 || mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")) {
            throw exception0(400,"会员注册找回目标格式不正确");
        }
        if (TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId()!=null
                && !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw exception0(403,"会员目标与当前店铺不匹配");
        }
    }
    private void requireIpLiteral(String ip) {
        // IPv4直接检查四段，只有含冒号的IPv6字面量交给JDK解析；不接受域名/转发头列表/zone。
        if (ip!=null && ip.length()<=45) {
            if (ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
                boolean valid=true;
                for (String part:ip.split("\\.")) { if (Integer.parseInt(part)>255) { valid=false; } }
                if (valid) { return; }
            } else if (ip.contains(":") && ip.matches("[0-9A-Fa-f:.]+")) {
                try { InetAddress.getByName(ip);return; }
                catch (UnknownHostException invalid) { /* 统一参数错误，不输出原始请求。 */ }
            }
        }
        throw exception0(400,"注册来源地址格式不正确");
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException unavailable() {
        return exception0(409,"该会员账号当前无法执行此操作，请登录或联系管理员");
    }
}
