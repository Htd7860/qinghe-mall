package cn.qinghe.mall.module.system.controller.app.storefront;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.app.storefront.vo.StorefrontRespVO;
import cn.qinghe.mall.module.system.service.tenant.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原版getTenantByWebsite职责；Host从实际请求头取，不接受website/host查询参数或转发头替代。 */
@RestController
@RequiredArgsConstructor
public class AppStorefrontController {
    private final StorefrontService storefront;
    @GetMapping("/app-api/storefront/resolve")
    @PermitAll
    public CommonResult<StorefrontRespVO> resolve(@RequestParam(value="store",required=false) String code,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        List<String> hosts=Collections.list(request.getHeaders("Host"));
        if (hosts.size()!=1) { throw exception0(400,"请提供唯一入口Host"); }
        StorefrontRespVO result=storefront.resolve(hosts.get(0),code);
        Long claimed=TenantContextHolder.getTenantId();
        if (claimed!=null && !claimed.equals(result.getTenantId())) { throw exception0(403,"入口与请求租户不匹配"); }
        return CommonResult.success(result);
    }
}
