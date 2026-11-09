package cn.qinghe.mall.module.product.controller.internal.trade;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.product.service.spu.ProductTradeReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;
/** 固定内部销售快照；原Bearer独立校验，Gateway不暴露此路径。
 * @author 芋道源码
 */
@RestController @RequiredArgsConstructor @RequestMapping("/internal-api/product/trade-skus")
public class InternalTradeSkuController {
    private final ProductTradeReadService service;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/list") public CommonResult<List<ProductTradeSkuRespDTO>> list(@RequestBody List<Long> ids){return CommonResult.success(service.list(ids));}
    @GetMapping("/page") public CommonResult<PageResult<ProductTradeSkuRespDTO>> page(@RequestParam(required=false) String keyword,@Valid PageParam page,@RequestParam(required=false) Long spuId){return CommonResult.success(service.page(keyword,page,spuId));}
}
