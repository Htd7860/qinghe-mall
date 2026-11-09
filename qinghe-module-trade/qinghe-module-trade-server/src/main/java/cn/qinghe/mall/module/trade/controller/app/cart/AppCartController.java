package cn.qinghe.mall.module.trade.controller.app.cart;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.trade.controller.app.cart.vo.*;
import cn.qinghe.mall.module.trade.service.cart.CartServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
/** 原购物车七方法及当前页实际商品选择入口，均仅当前会员/店铺。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/app-api/trade/cart")
public class AppCartController {
    private final CartServiceImpl service;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/add") public CommonResult<Long> add(@Valid @RequestBody AppCartAddReqVO body,@RequestParam(required=false) String store,HttpServletRequest request){return CommonResult.success(service.add(request,store,body));}
    @PutMapping("/update-count") public CommonResult<Boolean> count(@Valid @RequestBody AppCartUpdateCountReqVO body,@RequestParam(required=false) String store,HttpServletRequest request){service.count(request,store,body);return CommonResult.success(true);}
    @PutMapping("/update-selected") public CommonResult<Boolean> selected(@Valid @RequestBody AppCartUpdateSelectedReqVO body,@RequestParam(required=false) String store,HttpServletRequest request){service.selected(request,store,body);return CommonResult.success(true);}
    @PutMapping("/reset") public CommonResult<Long> reset(@Valid @RequestBody AppCartResetReqVO body,@RequestParam(required=false) String store,HttpServletRequest request){return CommonResult.success(service.reset(request,store,body));}
    @DeleteMapping("/delete") public CommonResult<Boolean> delete(@Valid AppCartDeleteReqVO body,@RequestParam(required=false) String store,HttpServletRequest request){service.delete(request,store,body);return CommonResult.success(true);}
    @GetMapping("/get-count") public CommonResult<Integer> countAll(@RequestParam(required=false) String store,HttpServletRequest request){return CommonResult.success(service.countAll(request,store));}
    @GetMapping("/list") public CommonResult<AppCartListRespVO> list(@RequestParam(required=false) String store,HttpServletRequest request){return CommonResult.success(service.list(request,store));}
    @GetMapping("/product-options") public CommonResult<PageResult<ProductTradeSkuRespDTO>> options(@RequestParam(required=false) String keyword,
        @RequestParam(defaultValue="1") @Min(1) @Max(10000) Integer pageNo,@RequestParam(required=false) Long spuId,@RequestParam(required=false) String store,HttpServletRequest request){return CommonResult.success(service.options(request,store,keyword,pageNo,spuId));}
}
