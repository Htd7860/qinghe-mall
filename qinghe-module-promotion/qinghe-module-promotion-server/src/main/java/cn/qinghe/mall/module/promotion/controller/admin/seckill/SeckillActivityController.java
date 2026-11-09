package cn.qinghe.mall.module.promotion.controller.admin.seckill;

import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.*;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillActivityDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 直接择取原活动create/update/get/page入口与权限，委托现有草稿实现。
 * 新建仅草稿，不开放原版直接启用、关闭、删除或消费者list-by-ids接口。
 * @author halfninety
 */
@RestController
@RequestMapping("/admin-api/promotion/seckill-activity")
@Validated
@RequiredArgsConstructor
public class SeckillActivityController {
    private final SeckillActivityDraftService service;

    @ModelAttribute
    public void prepare(HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");SecurityFrameworkUtils.requireBackendRealm(request);
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:create')")
    public CommonResult<SeckillActivityDraftRespVO> create(@Valid @RequestBody SeckillActivitySaveReqVO body) {
        if (body.getId()!=null || body.getExpectedVersion()!=null) throw exception0(400,"新建活动不能指定已有编号或版本");
        return CommonResult.success(service.save(body));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:update')")
    public CommonResult<SeckillActivityDraftRespVO> update(@Valid @RequestBody SeckillActivitySaveReqVO body) {
        if (body.getId()==null || body.getExpectedVersion()==null) throw exception0(400,"修改活动须提供当前编号和版本");
        return CommonResult.success(service.save(body));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query')")
    public CommonResult<SeckillActivityDraftRespVO> get(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id) {
        return CommonResult.success(service.get(id));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query')")
    public CommonResult<PageResult<SeckillActivityPageRespVO>> page(@Valid SeckillActivityPageReqVO query) {
        return CommonResult.success(service.page(query));
    }
}
