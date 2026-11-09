package cn.qinghe.mall.module.trade.framework.product;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@FeignClient(name="qinghe-trade-product",contextId="qingheTradeProduct",url="${qinghe.trade.product-base-url:http://127.0.0.1:8082}",configuration=SystemSecurityFeignConfiguration.class)
public interface TradeProductApi {
    @PostMapping("/internal-api/product/trade-skus/list") CommonResult<List<ProductTradeSkuRespDTO>> list(@RequestHeader("Authorization") String bearer,@RequestBody List<Long> ids);
    @GetMapping("/internal-api/product/trade-skus/page") CommonResult<PageResult<ProductTradeSkuRespDTO>> page(@RequestHeader("Authorization") String bearer,
        @RequestParam("keyword") String keyword,@RequestParam("pageNo") Integer pageNo,@RequestParam("pageSize") Integer pageSize,@RequestParam(value="spuId",required=false) Long spuId);
}
