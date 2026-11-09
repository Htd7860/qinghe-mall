package cn.qinghe.mall.module.promotion.controller.platform.seckill;

import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.platform.seckill.vo.*;
import cn.qinghe.mall.module.promotion.service.seckill.PlatformSeckillReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;

/** 沿用原活动分页Controller委托职责；平台专用只读白名单。 @author halfninety */
@RestController
@RequestMapping("/platform-api/promotion/seckill-operation")
@RequiredArgsConstructor @Validated
@PreAuthorize("@ss.hasPermission('promotion:seckill-operation:query')")
public class PlatformSeckillReadController {
    private final PlatformSeckillReadService service;
    @ModelAttribute public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/page")
    public CommonResult<PageResult<PlatformSeckillRowRespVO>> page(@Valid PlatformSeckillPageReqVO query) {
        return CommonResult.success(service.page(query));
    }
    @GetMapping("/audit-page")
    public CommonResult<PageResult<PlatformSeckillAuditRespVO>> audits(
            @RequestParam("tenantId") @Min(1) @Max(9007199254740991L) Long tenant,
            @RequestParam("id") @Min(1) @Max(9007199254740991L) Long activity,@Valid PageParam page) {
        return CommonResult.success(service.audits(tenant,activity,page));
    }
}
