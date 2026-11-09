package cn.qinghe.mall.module.promotion.controller.app.coupon;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.promotion.controller.app.coupon.vo.*;
import cn.qinghe.mall.module.promotion.service.coupon.AppCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
/** 直接择取原take/page/get职责；本人身份和实际店铺由Service从当前请求确认。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/app-api/promotion/coupon")
public class AppCouponController {
    private final AppCouponService service;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/take")
    public CommonResult<AppCouponGrantRespVO> take(@Valid @RequestBody AppCouponTakeReqVO body,
            @RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(service.take(request,store,body));
    }
    @GetMapping("/take-result")
    public CommonResult<AppCouponGrantRespVO> result(@Valid AppCouponTakeReqVO body,
            @RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(service.result(request,store,body));
    }
    @GetMapping("/page")
    public CommonResult<PageResult<AppCouponRespVO>> page(@Valid AppCouponPageReqVO page,
            @RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(service.ownedPage(request,store,page));
    }
    @GetMapping("/get")
    public CommonResult<AppCouponRespVO> get(@RequestParam @Min(1) @Max(9007199254740991L) Long id,
            @RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(service.get(request,store,id));
    }
}
