package cn.qinghe.mall.module.pay.controller.internal.order;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.pay.api.order.dto.*;
import cn.qinghe.mall.module.pay.service.order.PayOrderServiceImpl;
import cn.qinghe.mall.module.pay.service.order.PayOrderCloseService;
import cn.qinghe.mall.module.pay.service.order.PayOrderSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

/** 原PayOrderApi委托，仅trade专用服务凭据可用，无浏览器创建/支付成功入口。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalPayOrderController {
    public static final String CREATE="/internal-api/pay/order/create";
    public static final String RESULT="/internal-api/pay/order/result";
    public static final String CLOSE="/internal-api/pay/order/close";
    public static final String SYNCHRONIZE="/internal-api/pay/order/synchronize";
    public static final String SUBMIT="/internal-api/pay/order/submit";
    public static final String SUBMISSION="/internal-api/pay/order/submission-result";
    public static final String RECOVER_QR="/internal-api/pay/order/recover-qr";
    private final PayOrderServiceImpl orders;
    private final PayOrderCloseService closing;
    private final PayOrderSubmissionService submissions;
    @PostMapping(RECOVER_QR)
    public CommonResult<PayOrderSubmissionRespDTO> recoverQr(@Valid @RequestBody PayOrderGetReqDTO body){return CommonResult.success(submissions.recoverQr(body));}
    @PostMapping(SUBMIT)
    public CommonResult<PayOrderSubmissionRespDTO> submit(@Valid @RequestBody PayOrderGetReqDTO body){return CommonResult.success(submissions.submit(body));}
    @PostMapping(SUBMISSION)
    public CommonResult<PayOrderSubmissionRespDTO> submission(@Valid @RequestBody PayOrderGetReqDTO body){return CommonResult.success(orders.submissionResult(body));}
    @PostMapping(CREATE)
    public CommonResult<PayOrderRespDTO> create(@Valid @RequestBody PayOrderCreateReqDTO body){return CommonResult.success(orders.createOrder(body));}
    @PostMapping(RESULT)
    public CommonResult<PayOrderRespDTO> result(@Valid @RequestBody PayOrderGetReqDTO body){return CommonResult.success(orders.getOrder(body));}
    @PostMapping(CLOSE)
    public CommonResult<PayOrderRespDTO> close(@Valid @RequestBody PayOrderCreateReqDTO body){return CommonResult.success(closing.closeOrder(body));}
    @PostMapping(SYNCHRONIZE)
    public CommonResult<PayOrderRespDTO> synchronize(@Valid @RequestBody PayOrderGetReqDTO body){return CommonResult.success(closing.synchronizeOrder(body));}
}
