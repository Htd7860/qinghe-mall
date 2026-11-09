package cn.qinghe.mall.module.member.service.auth;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthSessionRespVO;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.user.MemberUserMapper;
import cn.qinghe.mall.module.member.framework.auth.MemberStorefrontClient;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原AppMemberUserController.getUserInfo按当前用户重读资料的职责，适配现有Identity契约。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class MemberSessionService {
    private final MemberUserMapper users;
    private final MemberStorefrontClient storefronts;

    public AppAuthSessionRespVO current(HttpServletRequest request,String store) {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null) { throw exception0(401,"请先登录"); }
        MemberStorefrontRespDTO entry=storefronts.resolveRequest(request,store);
        if (!Objects.equals(entry.getTenantId(),actor.getTenantId())) { throw exception0(403,"当前身份不属于此店铺"); }
        MemberUserDO user=requireCurrentMember(false);
        AppAuthSessionRespVO result=new AppAuthSessionRespVO();result.setUserId(user.getId());
        result.setName(user.getNickname()==null || user.getNickname().trim().isEmpty()?"会员":user.getNickname());
        result.setTenantId(user.getTenantId().toString());result.setTenantCode(entry.getTenantCode());
        // 消费者业务按本人归属校验；不借用后台角色/套餐菜单作为会员权限。
        result.setExpiresAt(actor.getExpiresTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return result;
    }

    /** 仅本地会员重读；写调用者先开启事务，内部不进行远程RPC。 */
    public MemberUserDO requireCurrentMember(boolean lock) {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null) { throw exception0(401,"请先登录"); }
        if (!UserTypeEnum.MEMBER.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())) {
            throw exception0(403,"当前身份不能访问消费者入口");
        }
        if (actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L || actor.getExpiresTime()==null) {
            throw exception0(503,"当前身份无法生成浏览器投影");
        }
        MemberUserDO user=users.selectCurrent(actor.getId(),lock);
        if (user==null || !Integer.valueOf(0).equals(user.getStatus()) || !Objects.equals(actor.getTenantId(),user.getTenantId())
                || user.getCredentialVersion()==null || user.getCredentialVersion()<0 || actor.getInfo()==null
                || !user.getCredentialVersion().toString().equals(actor.getInfo().get("credentialVersion"))
                || !actor.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw exception0(401,"会员凭据已失效，请重新登录");
        }
        return user;
    }
}
