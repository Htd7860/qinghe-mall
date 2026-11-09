package cn.qinghe.mall.module.promotion.controller.app.coupon;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.promotion.controller.app.coupon.vo.*;
import cn.qinghe.mall.module.promotion.service.coupon.AppCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
/** 原App模板分页与canTake投影；本次会员领券中心要求登录后展示个人额度。
 * @author 芋道源码
 */
@RestController @RequiredArgsConstructor @RequestMapping("/app-api/promotion/coupon-template")
public class AppCouponTemplateController {
    private final AppCouponService service;
    @GetMapping("/page")
    public CommonResult<PageResult<AppCouponTemplateRespVO>> page(@Valid AppCouponPageReqVO page,
            @RequestParam(required=false) String store,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.templatePage(request,store,page));
    }
}
