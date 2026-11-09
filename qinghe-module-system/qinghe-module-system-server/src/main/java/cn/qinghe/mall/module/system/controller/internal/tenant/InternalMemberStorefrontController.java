package cn.qinghe.mall.module.system.controller.internal.tenant;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.api.tenant.MemberStorefrontApi;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.system.controller.app.storefront.vo.StorefrontRespVO;
import cn.qinghe.mall.module.system.service.tenant.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原getTenantByWebsite入口解析职责；直接复用StorefrontService，不另建解析规则。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
public class InternalMemberStorefrontController {
    private final StorefrontService storefront;
    @PostMapping(MemberStorefrontApi.PATH)
    @PreAuthorize("hasAuthority('internal:member:storefront')")
    public CommonResult<MemberStorefrontRespDTO> resolve(@Valid @RequestBody MemberStorefrontReqDTO body,
            HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        if (TenantContextHolder.isIgnore()) { throw exception0(403,"入口解析不能忽略租户规则"); }
        final StorefrontRespVO entry;
        try { entry=storefront.resolve(body.getRequestHost(),body.getStore()); }
        catch (ServiceException rejected) {
            Integer code=rejected.getCode();
            if (Integer.valueOf(400).equals(code)) {
                response.setStatus(HttpServletResponse.SC_OK);return CommonResult.error(400,"商城入口格式不正确");
            }
            if (Integer.valueOf(403).equals(code)) {
                response.setStatus(HttpServletResponse.SC_OK);return CommonResult.error(403,"商城入口与店铺不匹配");
            }
            if (Integer.valueOf(401).equals(code) || Integer.valueOf(404).equals(code)) {
                response.setStatus(HttpServletResponse.SC_OK);
                return CommonResult.error(404,"商城入口不存在或已停用");
            }
            throw rejected;
        }
        Long claimed=TenantContextHolder.getTenantId();
        if (claimed!=null && !claimed.equals(entry.getTenantId())) { throw exception0(403,"入口与请求租户不匹配"); }
        return CommonResult.success(BeanUtils.toBean(entry,MemberStorefrontRespDTO.class));
    }
}
