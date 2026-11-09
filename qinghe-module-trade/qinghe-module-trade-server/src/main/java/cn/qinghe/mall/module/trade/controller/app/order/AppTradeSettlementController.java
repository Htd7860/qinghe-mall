package cn.qinghe.mall.module.trade.controller.app.order;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.trade.controller.app.order.vo.*;
import cn.qinghe.mall.module.trade.service.price.TradePriceServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
/** 原AppTradeOrderController.settlement职责；保存报价改POST，成功结果单独GET，不创建订单。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/app-api/trade/order")
public class AppTradeSettlementController {
    private final TradePriceServiceImpl prices;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/settlement")
    public CommonResult<AppTradeSettlementRespVO> settlement(@Valid @RequestBody AppTradeSettlementReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(prices.settlement(request,store,body));
    }
    @GetMapping("/settlement-result")
    public CommonResult<AppTradeSettlementRespVO> result(@RequestParam @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") String requestId,
            @RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(prices.result(request,store,requestId));
    }
}
