package cn.qinghe.mall.module.promotion.controller.platform.seckill;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.platform.seckill.vo.*;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillActivityExpiryService;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 精确平台写入口，开关关闭时拒绝，不替换原商家身份。 */
@RestController @RequiredArgsConstructor
@RequestMapping("/platform-api/promotion/seckill-operation")
public class PlatformSeckillStopController {
    private final ObjectProvider<SeckillActivityExpiryService> stops;
    @PostMapping("/stop")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-operation:query') && @ss.hasPermission('promotion:seckill-operation:stop')")
    public CommonResult<PlatformSeckillStopRespVO> stop(@Valid @RequestBody PlatformSeckillStopReqVO body,HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
        SeckillActivityExpiryService service=stops.getIfAvailable();if(service==null)throw exception0(503,"秒杀运行管理尚未启用");
        return CommonResult.success(service.stopByPlatform(body));
    }
}
