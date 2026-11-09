package cn.qinghe.mall.module.product.controller.app.spu;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.product.controller.app.spu.vo.*;
import cn.qinghe.mall.module.product.service.search.AppProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

/** 基础搜索单独精确入口；搜索关闭/未追平返回503，原MySQL商品列表仍保留。 */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/app-api/product/spu")
public class AppProductSearchController {
    private final AppProductSearchService service;
    @GetMapping("/search-page") @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> page(@Valid AppProductSpuPageReqVO query,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return CommonResult.success(service.page(request,store,query));
    }
}
