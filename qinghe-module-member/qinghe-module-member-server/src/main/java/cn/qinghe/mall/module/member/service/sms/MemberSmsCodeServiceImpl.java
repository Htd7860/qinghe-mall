package cn.qinghe.mall.module.member.service.sms;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.member.dal.dataobject.sms.*;
import cn.qinghe.mall.module.member.dal.mysql.sms.*;
import cn.qinghe.mall.module.member.framework.sms.*;
import cn.qinghe.mall.module.member.service.auth.MemberPasswordService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原SmsCodeServiceImpl.create/send/validate/use流程按租户隔离和并发/一次性要求适配。
 * 网络发送不占数据库事务；验证码与本地账号变更共用一次事务，不把校验结果当跨请求授权票据。
 * @author 芋道源码
 */
@Service
public class MemberSmsCodeServiceImpl implements MemberSmsCodeService {
    private final MemberSmsSubjectMapper subjects;
    private final MemberSmsCodeMapper codes;
    private final MemberSmsSender sender;
    private final MemberSmsCodeProperties settings;
    private final MemberPasswordService passwords;
    private final PasswordEncoder encoder;
    private final String mode;
    private final TransactionTemplate transaction;
    private final Semaphore calls;
    private final SecureRandom random=new SecureRandom();
    private final Clock clock=Clock.system(ZoneId.of("Asia/Shanghai"));

    public MemberSmsCodeServiceImpl(MemberSmsSubjectMapper subjects,MemberSmsCodeMapper codes,MemberSmsSender sender,
            MemberSmsCodeProperties settings,MemberSmsProperties channel,MemberPasswordService passwords,PasswordEncoder encoder,PlatformTransactionManager manager) {
        this.subjects=subjects;this.codes=codes;this.sender=sender;this.settings=settings;this.passwords=passwords;this.encoder=encoder;
        this.mode=channel.getMode();this.calls=new Semaphore(settings.getMaxConcurrent());
        this.transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(10);
    }
    @Override
    public Long sendSmsCode(Long tenantId,String mobile,MemberSmsScene scene) {
        requireTarget(tenantId,mobile,scene);requireChannel();acquire();
        try {
            Prepared prepared=TenantUtils.execute(tenantId,() -> transaction.execute(status -> reserve(tenantId,mobile,scene)));
            if (!prepared.expireTime.isAfter(LocalDateTime.now(clock))) {
                finish(tenantId,mobile,prepared.id,"EXPIRED",null);throw exception0(400,"验证码发送准备已过期，请重新申请");
            }
            MemberSmsSendResult accepted;
            try {
                accepted=sender.send(prepared.id,tenantId,mobile,scene,prepared.code);
                if (accepted==null || !mode.equals(accepted.getProvider()) || !bounded(accepted.getRequestId()) || !bounded(accepted.getSerialNo())) {
                    throw new IllegalStateException("短信渠道受理结果不完整");
                }
            } catch (RuntimeException unknown) {
                finish(tenantId,mobile,prepared.id,"UNKNOWN",null);
                throw exception0(503,"短信发送结果未确认，请勿立即重复发送");
            }
            String state=prepared.expireTime.isAfter(LocalDateTime.now(clock))?"ACTIVE":"EXPIRED";
            finish(tenantId,mobile,prepared.id,state,accepted);
            if (!"ACTIVE".equals(state)) { throw exception0(400,"验证码受理时已过期，请重新申请"); }
            return prepared.id;
        } finally { calls.release(); }
    }
    @Override
    public void validateSmsCode(Long tenantId,String mobile,MemberSmsScene scene,String code) {
        verify(tenantId,mobile,scene,code,null);
    }
    @Override
    public <T> T useSmsCode(Long tenantId,String mobile,MemberSmsScene scene,String code,Supplier<T> mutation) {
        Objects.requireNonNull(mutation,"会员变更回调不能为空");return verify(tenantId,mobile,scene,code,mutation);
    }
    private <T> T verify(Long tenantId,String mobile,MemberSmsScene scene,String code,Supplier<T> mutation) {
        requireTarget(tenantId,mobile,scene);requireChannel();
        if (code==null || !code.matches("[0-9]{6}")) { throw rejected(); }
        acquire();
        try {
            Outcome<T> outcome=TenantUtils.execute(tenantId,() -> transaction.execute(status -> {
                lock(tenantId,mobile);
                MemberSmsCodeDO current=codes.latest(tenantId,mobile,scene.name());
                LocalDateTime now=LocalDateTime.now(clock);
                if (current==null || !"ACTIVE".equals(current.getState()) || !mode.equals(current.getProviderMode())) { return new Outcome<T>(false,null); }
                if (now.isBefore(current.getCreateTime())) { throw exception0(503,"验证码校验时钟暂不可用"); }
                if (!current.getExpireTime().isAfter(now)) {
                    if (codes.expire(tenantId,current.getId(),now)!=1) { throw new IllegalStateException("验证码过期状态保存失败"); }
                    return new Outcome<T>(false,null);
                }
                if (current.getFailedAttempts()>=settings.getMaxAttempts()) { return new Outcome<T>(false,null); }
                if (!passwords.isPasswordMatch(code,current.getCodeHash())) {
                    String next=current.getFailedAttempts()+1>=settings.getMaxAttempts()?"LOCKED":"ACTIVE";
                    if (codes.failed(tenantId,current.getId(),current.getFailedAttempts(),next,now)!=1) { throw new IllegalStateException("验证码错误次数保存失败"); }
                    return new Outcome<T>(false,null);
                }
                if (mutation==null) { return new Outcome<T>(true,null); }
                T result=mutation.get();
                // 回调可能耗时；消费点重新取时间，不能在过期后仍使用此前校验时刻。
                LocalDateTime usedAt=LocalDateTime.now(clock);
                if (usedAt.isBefore(current.getCreateTime())) { throw exception0(503,"验证码消费时钟暂不可用"); }
                if (codes.use(tenantId,current.getId(),settings.getMaxAttempts(),usedAt)!=1) { throw rejected(); }
                return new Outcome<T>(true,result);
            }));
            // 在事务已正常提交错误计数后再抛出，避免401引发回滚让攻击者无限猜测。
            if (!outcome.valid) { throw rejected(); }return outcome.value;
        } finally { calls.release(); }
    }
    private Prepared reserve(Long tenantId,String mobile,MemberSmsScene scene) {
        subjects.ensure(tenantId,mobile,LocalDate.now(clock));MemberSmsSubjectDO subject=lock(tenantId,mobile);
        LocalDateTime now=LocalDateTime.now(clock);LocalDate today=now.toLocalDate();
        if (subject.getQuotaDate().isAfter(today)) { throw exception0(503,"短信额度时钟暂不可用"); }
        if (subject.getLastSentAt()!=null && now.isBefore(subject.getLastSentAt().plusSeconds(settings.getSendFrequencySeconds()))) {
            throw exception0(429,"验证码发送过于频繁，请稍后再试");
        }
        int count=subject.getQuotaDate().equals(today)?subject.getDailyCount():0;
        if (count>=settings.getDailyMaximum()) { throw exception0(429,"该号码今日验证码次数已达上限"); }
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        MemberSmsCodeDO row=new MemberSmsCodeDO();row.setTenantId(tenantId);row.setMobile(mobile);row.setScene(scene.name());row.setProviderMode(mode);
        row.setCodeHash(encoder.encode(code));row.setState("PENDING");row.setFailedAttempts(0);row.setTodayIndex(count+1);
        row.setExpireTime(now.plusSeconds(settings.getExpireSeconds()));row.setCreateTime(now);row.setUpdateTime(now);row.setCreator("sms-code");row.setUpdater("sms-code");row.setDeleted(false);
        if (codes.insert(row)!=1 || row.getId()==null || subjects.reserve(tenantId,mobile,today,count+1,now)!=1) { throw new IllegalStateException("验证码发送准备保存失败"); }
        return new Prepared(row.getId(),code,row.getExpireTime());
    }
    private void finish(Long tenantId,String mobile,Long id,String state,MemberSmsSendResult result) {
        TenantUtils.execute(tenantId,() -> transaction.execute(status -> {
            lock(tenantId,mobile);
            if (codes.finish(tenantId,id,state,result==null?null:result.getRequestId(),result==null?null:result.getSerialNo(),LocalDateTime.now(clock))!=1) {
                throw exception0(503,"短信发送结果保存未确认");
            }
            return null;
        }));
    }
    private MemberSmsSubjectDO lock(Long tenantId,String mobile) {
        MemberSmsSubjectDO subject=subjects.lock(tenantId,mobile);
        if (subject==null) { throw rejected(); }
        if (!tenantId.equals(subject.getTenantId()) || !mobile.equals(subject.getMobile()) || subject.getQuotaDate()==null
                || subject.getDailyCount()==null || subject.getDailyCount()<0) { throw new IllegalStateException("短信额度数据异常"); }
        return subject;
    }
    private void requireTarget(Long tenantId,String mobile,MemberSmsScene scene) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("验证码入口必须在外层事务之外调用"); }
        if (tenantId==null || tenantId<=0 || mobile==null || !mobile.matches("^1[3-9][0-9]{9}$") || scene==null) { throw exception0(400,"验证码目标参数不正确"); }
        if (TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId()!=null && !tenantId.equals(TenantContextHolder.getTenantId())) { throw exception0(403,"验证码租户与当前入口不匹配"); }
    }
    private void requireChannel() { if (!("ALIYUN".equals(mode) || "TEST".equals(mode))) { throw exception0(503,"短信渠道尚未启用"); } }
    private void acquire() { if (!calls.tryAcquire()) { throw exception0(429,"验证码服务繁忙，请稍后重试"); } }
    private boolean bounded(String value) { return value!=null && !value.trim().isEmpty() && value.length()<=128; }
    private cn.qinghe.mall.framework.common.exception.ServiceException rejected() { return exception0(401,"验证码无效、过期或已使用"); }
    /** 原始code只短暂存在于发送调用栈，不序列化、不落库、不生成toString。 */
    private static final class Prepared {
        private final Long id;private final String code;private final LocalDateTime expireTime;
        private Prepared(Long id,String code,LocalDateTime expireTime) { this.id=id;this.code=code;this.expireTime=expireTime; }
    }
    private static final class Outcome<T> {
        private final boolean valid;private final T value;
        private Outcome(boolean valid,T value) { this.valid=valid;this.value=value; }
    }
}
