package cn.qinghe.mall.module.member.service.auth;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.member.dal.dataobject.logger.MemberLoginLogDO;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.logger.MemberLoginLogMapper;
import cn.qinghe.mall.module.member.dal.mysql.user.MemberUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原LoginLogServiceImpl.createLoginLog和会员createLoginLog/updateUserLogin职责的本地组合。
 * 成功记录与最近登录在member库同事务；与system的Token提交不是分布式事务。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class MemberLoginLogService {
    public enum Result { SUCCESS, BAD_CREDENTIALS, LIMITED, ERROR }
    private final MemberLoginLogMapper logs;
    private final MemberUserMapper users;

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,
            rollbackFor=Exception.class,timeout=10)
    public void recordSuccess(Long tenantId,Long userId,Long credentialVersion,String mobile,String peer,String traceId) {
        requireMetadata(tenantId,mobile,peer,traceId);
        if (userId==null || userId<=0 || credentialVersion==null || credentialVersion<0) {
            throw exception0(401,"登录结果缺少可信会员身份");
        }
        TenantUtils.execute(tenantId,() -> {
            MemberUserDO user=users.selectCurrent(userId,true);
            if (user==null || !Integer.valueOf(0).equals(user.getStatus())
                    || !Objects.equals(tenantId,user.getTenantId()) || !Objects.equals(mobile,user.getMobile())
                    || !Objects.equals(credentialVersion,user.getCredentialVersion())) {
                throw exception0(401,"会员凭据已变化，请重新登录");
            }
            LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
            if (users.updateLogin(tenantId,userId,credentialVersion,peer,now)!=1) {
                throw exception0(401,"会员登录信息已变化，请重新登录");
            }
            MemberLoginLogDO row=row(tenantId,mobile,peer,traceId,Result.SUCCESS,now);
            row.setUserId(userId);row.setCredentialVersion(credentialVersion);insert(row);
            return null;
        });
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,
            rollbackFor=Exception.class,timeout=10)
    public void recordFailure(Long tenantId,String mobile,String peer,String traceId,Result result) {
        requireMetadata(tenantId,mobile,peer,traceId);
        if (result==null || result==Result.SUCCESS) { throw new IllegalArgumentException("失败记录结果不正确"); }
        TenantUtils.execute(tenantId,() -> {
            insert(row(tenantId,mobile,peer,traceId,result,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS)));
            return null;
        });
    }
    private MemberLoginLogDO row(Long tenantId,String mobile,String peer,String traceId,Result result,LocalDateTime now) {
        MemberLoginLogDO row=new MemberLoginLogDO();row.setTenantId(tenantId);row.setTraceId(traceId);
        row.setSubjectHash(DigestUtil.sha256Hex("qinghe-app\n"+tenantId+"\n"+mobile));
        row.setPeerHash(DigestUtil.sha256Hex(peer));row.setResult(result.name());row.setCreateTime(now);
        return row;
    }
    private void insert(MemberLoginLogDO row) {
        if (logs.insert(row)!=1 || row.getId()==null || row.getId()<=0) {
            throw new IllegalStateException("会员登录记录保存失败");
        }
    }
    private void requireMetadata(Long tenantId,String mobile,String peer,String traceId) {
        MybatisUtils.requireWriteTransaction();
        if (tenantId==null || tenantId<=0 || mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")
                || peer==null || !peer.matches("[0-9A-Fa-f:.]{2,45}") || traceId==null || !traceId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("登录记录元数据不完整");
        }
        if (TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId()!=null
                && !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw exception0(403,"登录记录租户与入口不匹配");
        }
    }
}
