package cn.qinghe.mall.module.product.controller.admin.spu;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.*;
import cn.qinghe.mall.module.product.service.stock.ProductStockService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
/** B04手工库存与未知结果查询接线；不接受消费者/订单自由调用。 */
@RestController
@Validated
@RequestMapping("/admin-api/product/stock")
public class ProductStockController {
    private final ProductStockService service;
    public ProductStockController(ProductStockService service) { this.service=service; }
    @ModelAttribute
    public void prepare(javax.servlet.http.HttpServletRequest request,javax.servlet.http.HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @PostMapping("/adjust")
    @PreAuthorize("@ss.hasPermission('product:spu:update-stock')")
    public CommonResult<ProductStockOperationRespVO> adjust(@RequestBody @Valid ProductStockAdjustReqVO body) {
        return CommonResult.success(service.adjust(body));
    }
    @GetMapping("/operation")
    @PreAuthorize("@ss.hasPermission('product:spu:update-stock')")
    public CommonResult<ProductStockOperationRespVO> operation(@RequestParam("spuId") @Min(1) Long spuId,
            @RequestParam("operationKey") @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9:_-]{7,79}") String operationKey) {
        return CommonResult.success(service.getOperation(spuId,operationKey));
    }
}
