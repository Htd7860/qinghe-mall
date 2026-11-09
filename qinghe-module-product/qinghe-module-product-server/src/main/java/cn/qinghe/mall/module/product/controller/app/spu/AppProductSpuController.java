package cn.qinghe.mall.module.product.controller.app.spu;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.product.controller.app.spu.vo.*;
import cn.qinghe.mall.module.product.service.spu.AppProductSpuService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
/** 直接择取yudao App商品分页/上架详情入口；实际店铺定位后查询销售白名单。 @author 芋道源码 */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/app-api/product")
public class AppProductSpuController {
    private final AppProductSpuService service;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/spu/page") @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> page(@Valid AppProductSpuPageReqVO body,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(service.page(request,store,body));
    }
    @GetMapping("/spu/get-detail") @PermitAll
    public CommonResult<AppProductSpuDetailRespVO> detail(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(service.detail(request,store,id));
    }
    @GetMapping("/catalog/options") @PermitAll
    public CommonResult<AppProductCatalogRespVO> options(@RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(service.options(request,store));
    }
}
