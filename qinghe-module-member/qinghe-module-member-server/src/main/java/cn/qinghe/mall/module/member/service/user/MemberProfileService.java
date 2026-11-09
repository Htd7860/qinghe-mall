package cn.qinghe.mall.module.member.service.user;

import cn.qinghe.mall.module.member.controller.app.user.vo.AppMemberProfileRespVO;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.user.MemberUserMapper;
import cn.qinghe.mall.module.member.service.auth.MemberSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原MemberUserServiceImpl.getUser/updateUser本人白名单职责，营销退出为B10增量。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class MemberProfileService {
    private final MemberSessionService members;
    private final MemberUserMapper users;
    public AppMemberProfileRespVO current() { return response(members.requireCurrentMember(false)); }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=10)
    public AppMemberProfileRespVO updatePreference(Boolean optOut,Long version) {
        if (optOut==null || version==null || version<0) { throw exception0(400,"请提供营销偏好及当前版本"); }
        MemberUserDO user=members.requireCurrentMember(true);
        if (!Objects.equals(user.getAuthVersion(),version) || version==Long.MAX_VALUE) {
            throw exception0(409,"会员资料已变化，请重新读取");
        }
        if (Objects.equals(user.getMarketingOptOut(),optOut)) { return response(user); }
        if (users.updatePreference(user.getTenantId(),user.getId(),version,user.getCredentialVersion(),optOut)!=1
                || users.auditPreference(user.getTenantId(),user.getId(),user.getMarketingOptOut(),optOut,version,version+1)!=1) {
            throw new IllegalStateException("会员营销偏好保存未确认");
        }
        user.setMarketingOptOut(optOut);user.setAuthVersion(version+1);return response(user);
    }
    private AppMemberProfileRespVO response(MemberUserDO user) {
        if (user.getAuthVersion()==null || user.getAuthVersion()<0 || user.getMarketingOptOut()==null
                || user.getMobile()==null || !user.getMobile().matches("^1[3-9][0-9]{9}$")) {
            throw exception0(503,"会员资料不完整");
        }
        AppMemberProfileRespVO result=new AppMemberProfileRespVO();result.setId(user.getId());result.setNickname(user.getNickname());
        result.setMobileMasked(user.getMobile().substring(0,3)+"****"+user.getMobile().substring(7));
        result.setMarketingOptOut(user.getMarketingOptOut());result.setAuthVersion(user.getAuthVersion());return result;
    }
}
