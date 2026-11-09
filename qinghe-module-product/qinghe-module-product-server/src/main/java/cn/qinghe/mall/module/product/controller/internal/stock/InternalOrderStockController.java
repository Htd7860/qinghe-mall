package cn.qinghe.mall.module.product.controller.internal.stock;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.product.api.stock.dto.*;
import cn.qinghe.mall.module.product.api.stock.ProductStockReleaseReason;
import cn.qinghe.mall.module.product.service.stock.ProductOrderStockService;
import cn.qinghe.mall.module.product.service.stock.ProductAfterSaleStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

/** 择取原 ProductSkuApiImpl 委托；精确trade服务入口，售后数量只能来自原退款计划。
 * @author 芋道源码
 */
@RestController
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalOrderStockController {
    public static final String DEDUCT = "/internal-api/product/order-stock/deduct";
    public static final String RESULT = "/internal-api/product/order-stock/result";
    public static final String CLOSE_CREATION = "/internal-api/product/order-stock/close-creation";
    public static final String CLOSE_UNPAID = "/internal-api/product/order-stock/close-unpaid";
    public static final String RESTORE_REFUND = "/internal-api/product/order-stock/restore-refund";
    public static final String REFUND_RESULT = "/internal-api/product/order-stock/refund-result";
    private final ProductOrderStockService stocks;
    private final ProductAfterSaleStockService afterSales;

    @PostMapping(RESTORE_REFUND)
    public CommonResult<ProductAfterSaleStockRespDTO> restoreRefund(@Valid @RequestBody ProductAfterSaleStockReqDTO body) {
        return CommonResult.success(afterSales.restore(body));
    }
    @PostMapping(REFUND_RESULT)
    public CommonResult<ProductAfterSaleStockRespDTO> refundResult(@Valid @RequestBody ProductAfterSaleStockReqDTO body) {
        return CommonResult.success(afterSales.get(body));
    }

    @PostMapping(DEDUCT)
    public CommonResult<ProductOrderStockRespDTO> deduct(@RequestBody ProductOrderStockReqDTO body) {
        return CommonResult.success(stocks.deduct(body));
    }

    @PostMapping(RESULT)
    public CommonResult<ProductOrderStockRespDTO> result(@Valid @RequestBody ProductOrderStockResultReqDTO body) {
        return CommonResult.success(stocks.get(body.getOrderId(), body.getUserId()));
    }

    @PostMapping(CLOSE_CREATION)
    public CommonResult<ProductOrderStockRespDTO> closeCreation(@Valid @RequestBody ProductOrderStockResultReqDTO body) {
        return CommonResult.success(stocks.release(body.getOrderId(),body.getUserId(),ProductStockReleaseReason.CREATE_FAILED));
    }

    /** trade调用前已持久取消正式订单并确认未付关闭；不接受外部数量或自由回补理由。 */
    @PostMapping(CLOSE_UNPAID)
    public CommonResult<ProductOrderStockRespDTO> closeUnpaid(@Valid @RequestBody ProductOrderStockResultReqDTO body) {
        return CommonResult.success(stocks.release(body.getOrderId(),body.getUserId(),ProductStockReleaseReason.UNPAID_CLOSED));
    }
}
