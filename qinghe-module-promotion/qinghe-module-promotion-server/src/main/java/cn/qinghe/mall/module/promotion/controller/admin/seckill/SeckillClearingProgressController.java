package cn.qinghe.mall.module.promotion.controller.admin.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillClearingProgressRespVO;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillClearingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.constraints.*;

/** 沿用原后台query权限和委托形式；GET查询不推进清算。 @author halfninety */
@RestController
@RequestMapping("/admin-api/promotion/seckill-activity")
@Validated
@RequiredArgsConstructor
public class SeckillClearingProgressController {
    private final SeckillClearingProgressService service;
    @GetMapping("/clearing-progress")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query')")
    public CommonResult<SeckillClearingProgressRespVO> get(
            @RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,
            @RequestParam("generation") @Min(1) Integer generation,
            HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
        return CommonResult.success(service.get(id,generation));
    }
}
