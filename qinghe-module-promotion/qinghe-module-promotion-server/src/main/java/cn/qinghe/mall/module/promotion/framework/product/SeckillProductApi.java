package cn.qinghe.mall.module.promotion.framework.product;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 原 ProductSpuApi/ProductSkuApi 的最小活动快照调用。 */
@FeignClient(name="qinghe-seckill-product",contextId="qingheSeckillProduct",
        url="${qinghe.promotion.product-base-url:http://127.0.0.1:8082}",configuration=SystemSecurityFeignConfiguration.class)
public interface SeckillProductApi {
    @PostMapping("/internal-api/product/seckill/snapshot")
    CommonResult<SeckillProductSnapshotRespDTO> snapshot(@RequestHeader("Authorization") String bearer,
            @RequestBody SeckillProductSnapshotReqDTO body);
}
