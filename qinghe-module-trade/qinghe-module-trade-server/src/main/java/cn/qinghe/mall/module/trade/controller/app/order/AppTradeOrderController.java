package cn.qinghe.mall.module.trade.controller.app.order;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.trade.controller.app.order.vo.*;
import cn.qinghe.mall.module.trade.service.order.*;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderPaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原AppTradeOrderController的创建/本人分页详情/取消；无浏览器支付成功回调入口。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/app-api/trade/order")
public class AppTradeOrderController {
    private final TradeOrderAppService application;
    private final TradeOrderQueryService queries;
    private final TradeOrderCancelService cancellations;
    private final TradeOrderPaymentService payments;
    private final TradeOrderFulfillmentService fulfillment;
    @PostMapping("/receive")
    public CommonResult<Boolean> receive(@Valid @RequestBody AppTradeOrderReceiveReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        fulfillment.receiveByMember(request,store,body);
        return CommonResult.success(true);
    }
    @PostMapping("/recover-payment-qr")
    public CommonResult<AppTradeOrderPaymentRespVO> recoverPaymentQr(@Valid @RequestBody AppTradeOrderIdReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(payments.recoverQr(request,store,body.getId()));
    }
    @GetMapping("/payment-result")
    public CommonResult<AppTradeOrderPaymentRespVO> paymentResult(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(payments.paymentResult(request,store,id));
    }
    @PostMapping("/submit-payment")
    public CommonResult<AppTradeOrderPaymentRespVO> submitPayment(@Valid @RequestBody AppTradeOrderIdReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(payments.submit(request,store,body.getId()));
    }
    @PostMapping("/query-payment")
    public CommonResult<AppTradeOrderPaymentRespVO> queryPayment(@Valid @RequestBody AppTradeOrderIdReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(payments.queryPayment(request,store,body.getId()));
    }
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/create")
    public CommonResult<AppTradeOrderCreateRespVO> create(@Valid @RequestBody AppTradeOrderCreateReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(application.create(request,store,body));
    }
    @PostMapping("/stop-create")
    public CommonResult<AppTradeOrderStopRespVO> stop(@Valid @RequestBody AppTradeOrderCreateReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(application.stopUnprepared(request,store,body));
    }
    @GetMapping("/create-result")
    public CommonResult<AppTradeOrderCreateRespVO> result(@RequestParam("requestId") String requestId,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(queries.creationResult(request,store,requestId));
    }
    @GetMapping("/page")
    public CommonResult<PageResult<AppTradeOrderPageItemRespVO>> page(@Valid AppTradeOrderPageReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(queries.page(request,store,body));
    }
    @GetMapping("/get-detail")
    public CommonResult<AppTradeOrderDetailRespVO> detail(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(queries.detail(request,store,id));
    }
    @PostMapping("/prepare-payment")
    public CommonResult<TradeOrderPaymentResult> preparePayment(@Valid @RequestBody AppTradeOrderIdReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        return CommonResult.success(payments.prepare(request,store,body.getId()));
    }
    @PostMapping("/cancel")
    public CommonResult<Boolean> cancel(@Valid @RequestBody AppTradeOrderIdReqVO body,@RequestParam(required=false) String store,HttpServletRequest request) {
        if(!cancellations.cancelByMember(request,store,body.getId()))throw exception0(409,"原订单已有付款，不能按未付取消");
        return CommonResult.success(true);
    }
}
