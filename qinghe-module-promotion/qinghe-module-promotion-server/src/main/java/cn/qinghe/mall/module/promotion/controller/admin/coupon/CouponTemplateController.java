package cn.qinghe.mall.module.promotion.controller.admin.coupon;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.qinghe.mall.module.promotion.service.coupon.CouponTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.stream.Collectors;
/** 直接择取原模板六个精确CRUD/分页入口，适配当前版本契约及显式/admin-api。
 * @author 芋道源码
 */
@RestController @RequestMapping("/admin-api/promotion/coupon-template")
@Validated @RequiredArgsConstructor
public class CouponTemplateController {
    private final CouponTemplateService service;
    @ModelAttribute
    public void prepare(HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");SecurityFrameworkUtils.requireBackendRealm(request);
    }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:create')")
    public CommonResult<Long> createCouponTemplate(@Valid @RequestBody CouponTemplateCreateReqVO body) {
        return CommonResult.success(service.createCouponTemplate(body));
    }
    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:update')")
    public CommonResult<Boolean> updateCouponTemplate(@Valid @RequestBody CouponTemplateUpdateReqVO body) {
        service.updateCouponTemplate(body.getId(),body.getExpectedVersion(),body);return CommonResult.success(true);
    }
    @PutMapping("/update-status")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:update')")
    public CommonResult<Boolean> updateCouponTemplateStatus(@Valid @RequestBody CouponTemplateUpdateStatusReqVO body) {
        service.updateCouponTemplateStatus(body.getId(),body.getExpectedVersion(),body.getStatus());return CommonResult.success(true);
    }
    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:delete')")
    public CommonResult<Boolean> deleteCouponTemplate(@RequestParam("id") @Min(1) Long id,
            @RequestParam("expectedVersion") @Min(0) Long expectedVersion) {
        service.deleteCouponTemplate(id,expectedVersion);return CommonResult.success(true);
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:query')")
    public CommonResult<CouponTemplateRespVO> getCouponTemplate(@RequestParam("id") @Min(1) Long id) {
        return CommonResult.success(BeanUtils.toBean(service.getCouponTemplate(id),CouponTemplateRespVO.class));
    }
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:query')")
    public CommonResult<PageResult<CouponTemplateRespVO>> getCouponTemplatePage(@Valid CouponTemplatePageReqVO body) {
        PageResult<CouponTemplateDO> page=service.getCouponTemplatePage(body);
        return CommonResult.success(new PageResult<>(page.getTotal(),page.getList().stream()
                .map(row -> BeanUtils.toBean(row,CouponTemplateRespVO.class)).collect(Collectors.toList())));
    }
}
