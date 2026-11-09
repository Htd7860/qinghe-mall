package cn.qinghe.mall.module.trade.controller.app.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillQuoteService;
import cn.qinghe.mall.module.trade.service.seckill.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用原会员结算Controller委托；秒杀只报价、不走普通库存/券/购物车准备。 @author 芋道源码 */
@RestController @Validated
@RequestMapping("/app-api/trade/seckill")
@PreAuthorize("isAuthenticated()")
public class AppSeckillQuoteController {
    private final TradeSeckillQuoteService service;
    private final boolean enabled;
    public AppSeckillQuoteController(TradeSeckillQuoteService service,@Value("${qinghe.trade.seckill-orders.enabled:false}") boolean enabled) {
        this.service=service;this.enabled=enabled;
    }
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/quote")
    public CommonResult<TradeSeckillQuoteReceiptDTO> quote(@Valid @RequestBody TradeSeckillQuoteReqDTO body,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        if(!enabled)throw exception0(503,"秒杀购买尚未开放");
        return CommonResult.success(service.quote(request,store,body));
    }
    @GetMapping("/quote-result")
    public CommonResult<TradeSeckillQuoteReceiptDTO> result(
            @RequestParam("requestId") @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") String requestId,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(service.result(request,store,requestId));
    }
}
