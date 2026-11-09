package cn.qinghe.mall.module.product.controller.internal.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.product.service.seckill.ProductSeckillAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/** 原ProductSkuApiImpl委托结构；只认专用promotion服务身份，Gateway不路由。 @author LeeYan9 */
@RestController
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:promotion:seckill-stock')")
public class InternalSeckillStockController {
    public static final String ALLOCATE="/internal-api/product/seckill-stock/allocate";
    public static final String RESULT="/internal-api/product/seckill-stock/result";
    public static final String COMPENSATE="/internal-api/product/seckill-stock/compensate";
    public static final String RETURN="/internal-api/product/seckill-stock/return";
    private final ProductSeckillAllocationService service;

    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }

    @PostMapping(ALLOCATE)
    public CommonResult<ProductSeckillAllocationRespDTO> allocate(@Valid @RequestBody ProductSeckillAllocationReqDTO body) {
        return CommonResult.success(service.allocate(body));
    }
    @PostMapping(RESULT)
    public CommonResult<ProductSeckillAllocationRespDTO> result(@Valid @RequestBody ProductSeckillAllocationReqDTO body) {
        return CommonResult.success(service.get(body));
    }
    @PostMapping(COMPENSATE)
    public CommonResult<ProductSeckillAllocationRespDTO> compensate(@Valid @RequestBody ProductSeckillAllocationReqDTO body) {
        return CommonResult.success(service.compensate(body));
    }
    @PostMapping(RETURN)
    public CommonResult<ProductSeckillAllocationRespDTO> returnStock(@Valid @RequestBody ProductSeckillReturnReqDTO body) {
        return CommonResult.success(service.returnStock(body));
    }
}
