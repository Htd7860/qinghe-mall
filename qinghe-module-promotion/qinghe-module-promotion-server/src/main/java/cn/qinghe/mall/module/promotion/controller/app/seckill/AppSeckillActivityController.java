package cn.qinghe.mall.module.promotion.controller.app.seckill;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.promotion.controller.app.seckill.vo.AppSeckillActivityRespVO;
import cn.qinghe.mall.module.promotion.service.seckill.AppSeckillActivityService;
import lombok.RequiredArgsConstructor;
import javax.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;

/** 真实店铺公开销售浏览，不接收租户定位参数；购买和本人结果仍需登录。 @author 芋道源码 */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/app-api/promotion/seckill-activity")
public class AppSeckillActivityController {
    private final AppSeckillActivityService service;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/page")
    @PermitAll
    public CommonResult<PageResult<AppSeckillActivityRespVO>> page(@Valid PageParam query,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(service.page(request,store,query));
    }
    @GetMapping("/get")
    @PermitAll
    public CommonResult<AppSeckillActivityRespVO> get(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(service.get(request,store,id));
    }
}
