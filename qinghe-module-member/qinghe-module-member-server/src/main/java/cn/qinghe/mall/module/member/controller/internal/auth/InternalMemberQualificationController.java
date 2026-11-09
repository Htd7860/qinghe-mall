package cn.qinghe.mall.module.member.controller.internal.auth;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.member.api.auth.MemberQualificationApi;
import cn.qinghe.mall.module.member.api.auth.dto.*;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.user.MemberUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 择取原MemberUserApiImpl真实用户查询/validate职责，增加启用状态与会话代次。
 * 只有专用service principal可调用，不接普通用户自报身份作证明。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
public class InternalMemberQualificationController {
    private final MemberUserMapper users;
    @PostMapping(MemberQualificationApi.PATH)
    @PreAuthorize("hasAuthority('internal:system:member-qualification')")
    public CommonResult<MemberQualificationRespDTO> check(@Valid @RequestBody MemberQualificationReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        Long claimed=TenantContextHolder.getTenantId();
        if (TenantContextHolder.isIgnore() || claimed!=null && !Objects.equals(claimed,body.getTenantId())) {
            throw exception0(403,"内部会员资格租户不匹配");
        }
        try {
            return TenantUtils.execute(body.getTenantId(),() -> {
                MemberUserDO user=users.selectCurrent(body.getUserId(),false);
                if (user==null || !Integer.valueOf(0).equals(user.getStatus()) || user.getCredentialVersion()==null
                        || user.getCredentialVersion()<0 || body.getCredentialVersion()!=null && !Objects.equals(body.getCredentialVersion(),user.getCredentialVersion())) {
                    throw exception0(401,"会员凭据已失效");
                }
                MemberQualificationRespDTO result=new MemberQualificationRespDTO();result.setTenantId(user.getTenantId());result.setUserId(user.getId());
                result.setNickname(user.getNickname());result.setCredentialVersion(user.getCredentialVersion());return CommonResult.success(result);
            });
        } catch (ServiceException invalid) {
            // 本方法已通过服务身份校验；业务失效与过滤器的服务Bearer错误分开。
            if (Integer.valueOf(401).equals(invalid.getCode())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return CommonResult.error(401,"会员凭据已失效");
            }
            throw invalid;
        }
    }
}
