package cn.qinghe.mall.module.promotion.framework.trade;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="qinghe-seckill-quote-binding",contextId="qingheSeckillQuoteBinding",url="${qinghe.promotion.trade-base-url:http://127.0.0.1:8083}",configuration=SeckillQuoteBindingFeignConfiguration.class)
public interface SeckillQuoteBindingApi {
    @PostMapping("/internal-api/trade/seckill-order/refund-state")
    CommonResult<SeckillRefundSnapshotDTO> refundState(@RequestHeader("Authorization") String bearer,@RequestHeader("tenant-id") Long tenant,@RequestBody SeckillRefundLookupDTO body);
    @PostMapping("/internal-api/trade/seckill-order/state")
    CommonResult<SeckillOrderStateDTO> orderState(@RequestHeader("Authorization") String bearer,@RequestHeader("tenant-id") Long tenant,@RequestBody SeckillQuoteBindingReqDTO body);
    @PostMapping("/internal-api/trade/seckill-order/result-event")
    CommonResult<SeckillOrderResultSnapshotDTO> orderResult(@RequestHeader("Authorization") String bearer,@RequestHeader("tenant-id") Long tenant,@RequestBody SeckillOrderResultLookupDTO body);
    @PostMapping("/internal-api/trade/seckill-quote/bind")
    CommonResult<SeckillQuoteBindingRespDTO> bind(@RequestHeader("Authorization") String bearer,@RequestHeader("tenant-id") Long tenant,@RequestBody SeckillQuoteBindingReqDTO body);
    @PostMapping("/internal-api/trade/seckill-quote/result")
    CommonResult<SeckillQuoteBindingRespDTO> result(@RequestHeader("Authorization") String bearer,@RequestHeader("tenant-id") Long tenant,@RequestBody SeckillQuoteBindingReqDTO body);
    @PostMapping("/internal-api/trade/seckill-quote/close")
    CommonResult<SeckillQuoteBindingRespDTO> close(@RequestHeader("Authorization") String bearer,@RequestHeader("tenant-id") Long tenant,@RequestBody SeckillQuoteBindingReqDTO body);
}
