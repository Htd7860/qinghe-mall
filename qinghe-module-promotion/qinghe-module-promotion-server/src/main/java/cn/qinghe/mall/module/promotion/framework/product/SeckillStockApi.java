package cn.qinghe.mall.module.promotion.framework.product;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 原ProductSkuApi API/server分层委托，精确路径仅服务端使用。 @author LeeYan9 */
@FeignClient(name="qinghe-seckill-stock",contextId="qingheSeckillStock",url="${qinghe.promotion.product-base-url:http://127.0.0.1:8082}",
        configuration=SeckillStockFeignConfiguration.class)
public interface SeckillStockApi {
    @PostMapping("/internal-api/product/seckill-stock/return")
    CommonResult<ProductSeckillAllocationRespDTO> returnStock(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductSeckillReturnReqDTO body);
    @PostMapping("/internal-api/product/seckill-stock/allocate")
    CommonResult<ProductSeckillAllocationRespDTO> allocate(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductSeckillAllocationReqDTO body);
    @PostMapping("/internal-api/product/seckill-stock/result")
    CommonResult<ProductSeckillAllocationRespDTO> result(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductSeckillAllocationReqDTO body);
    @PostMapping("/internal-api/product/seckill-stock/compensate")
    CommonResult<ProductSeckillAllocationRespDTO> compensate(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody ProductSeckillAllocationReqDTO body);
}
