package cn.qinghe.mall.module.promotion.controller.internal.seckill;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillEventReadService;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillResourceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;

@RestController @RequiredArgsConstructor @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalSeckillEventController {
    public static final String READ="/internal-api/promotion/seckill-event/read";
    public static final String RESOURCE="/internal-api/promotion/seckill-order/resource-result";
    public static final String PAYMENT_ELIGIBILITY="/internal-api/promotion/seckill-order/payment-eligibility";
    private final SeckillEventReadService service;
    private final SeckillResourceReadService resources;
    @PostMapping(PAYMENT_ELIGIBILITY)
    public CommonResult<SeckillPaymentEligibilityDTO> paymentEligibility(@RequestBody SeckillResourceLookupDTO request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(resources.paymentEligibility(request));
    }
    @PostMapping(RESOURCE)
    public CommonResult<SeckillResourceReceiptDTO> resource(@RequestBody SeckillResourceLookupDTO request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(resources.get(request));
    }
    @PostMapping(READ)
    public CommonResult<SeckillDispatchSnapshotDTO> read(@RequestBody SeckillEventLookupReqDTO request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.get(request));
    }
}
