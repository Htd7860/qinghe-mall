package cn.qinghe.mall.module.trade.controller.internal.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillQuoteBindingService;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillResultReadService;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillRefundReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;

/** 原内部API固定引用/Service委托方式；精确POST含只读result与订单结果事件，均需专用服务身份。 */
@RestController @RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalSeckillQuoteBindingController {
    public static final String BIND="/internal-api/trade/seckill-quote/bind";
    public static final String RESULT="/internal-api/trade/seckill-quote/result";
    public static final String CLOSE="/internal-api/trade/seckill-quote/close";
    public static final String ORDER_RESULT="/internal-api/trade/seckill-order/result-event";
    public static final String ORDER_STATE="/internal-api/trade/seckill-order/state";
    public static final String REFUND_STATE="/internal-api/trade/seckill-order/refund-state";
    private final TradeSeckillQuoteBindingService service;
    private final TradeSeckillResultReadService results;
    private final TradeSeckillRefundReadService refunds;
    @PostMapping(REFUND_STATE)
    public CommonResult<SeckillRefundSnapshotDTO> refundState(@RequestBody SeckillRefundLookupDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(refunds.get(body));
    }
    @PostMapping(ORDER_STATE)
    public CommonResult<SeckillOrderStateDTO> orderState(@RequestBody SeckillQuoteBindingReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(results.state(body));
    }
    @PostMapping(ORDER_RESULT)
    public CommonResult<SeckillOrderResultSnapshotDTO> orderResult(@RequestBody SeckillOrderResultLookupDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(results.get(body));
    }
    @PostMapping(BIND)
    public CommonResult<SeckillQuoteBindingRespDTO> bind(@RequestBody SeckillQuoteBindingReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.bind(body));
    }
    @PostMapping(RESULT)
    public CommonResult<SeckillQuoteBindingRespDTO> result(@RequestBody SeckillQuoteBindingReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.result(body));
    }
    @PostMapping(CLOSE)
    public CommonResult<SeckillQuoteBindingRespDTO> close(@RequestBody SeckillQuoteBindingReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.close(body));
    }
}
