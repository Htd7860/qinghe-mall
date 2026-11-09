package cn.qinghe.mall.module.trade.framework.order;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.product.api.stock.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 择取原 ProductSkuApi 批量委托，服务Bearer与租户均由trade持久记录构造。
 * @author 芋道源码
 */
@FeignClient(name="qinghe-order-stock",contextId="qingheOrderStock",url="${qinghe.trade.product-base-url:http://127.0.0.1:8082}",configuration=OrderResourceFeignConfiguration.class)
public interface OrderStockFeignApi {
    @PostMapping("/internal-api/product/order-stock/restore-refund")
    CommonResult<ProductAfterSaleStockRespDTO> restoreRefund(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductAfterSaleStockReqDTO body);
    @PostMapping("/internal-api/product/order-stock/refund-result")
    CommonResult<ProductAfterSaleStockRespDTO> refundResult(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductAfterSaleStockReqDTO body);
    @PostMapping("/internal-api/product/order-stock/deduct")
    CommonResult<ProductOrderStockRespDTO> deduct(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductOrderStockReqDTO body);
    @PostMapping("/internal-api/product/order-stock/result")
    CommonResult<ProductOrderStockRespDTO> result(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductOrderStockResultReqDTO body);
    @PostMapping("/internal-api/product/order-stock/close-creation")
    CommonResult<ProductOrderStockRespDTO> closeCreation(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductOrderStockResultReqDTO body);
    @PostMapping("/internal-api/product/order-stock/close-unpaid")
    CommonResult<ProductOrderStockRespDTO> closeUnpaid(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductOrderStockResultReqDTO body);
}
