package cn.qinghe.mall.module.system.controller.internal.tenant;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.system.controller.app.storefront.vo.StorefrontRespVO;
import cn.qinghe.mall.module.system.service.tenant.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 复用既有入口解析，供业务服务携原MEMBER Bearer核对实际请求Host；不使用会员签发密钥。 */
@RestController @RequiredArgsConstructor
public class InternalCurrentStorefrontController {
    private final StorefrontService storefront;
    @PostMapping("/internal-api/auth/storefront/current")
    public CommonResult<MemberStorefrontRespDTO> current(@Valid @RequestBody MemberStorefrontReqDTO body,
            HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || !UserTypeEnum.MEMBER.getValue().equals(actor.getUserType())
                || actor.getTenantId()==null || actor.getTenantId()<=0 || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"当前入口需要会员身份");
        try {
            StorefrontRespVO entry=storefront.resolve(body.getRequestHost(),body.getStore());
            if(!Objects.equals(actor.getTenantId(),entry.getTenantId()))return CommonResult.error(403,"当前身份不属于此店铺");
            return CommonResult.success(BeanUtils.toBean(entry,MemberStorefrontRespDTO.class));
        } catch(ServiceException rejected) {
            Integer code=rejected.getCode();
            if(Integer.valueOf(400).equals(code))return CommonResult.error(400,"商城入口格式不正确");
            if(Integer.valueOf(403).equals(code))return CommonResult.error(403,"当前身份不属于此店铺");
            if(Integer.valueOf(401).equals(code) || Integer.valueOf(404).equals(code))return CommonResult.error(404,"商城入口不存在或已停用");
            throw rejected;
        }
    }
}
