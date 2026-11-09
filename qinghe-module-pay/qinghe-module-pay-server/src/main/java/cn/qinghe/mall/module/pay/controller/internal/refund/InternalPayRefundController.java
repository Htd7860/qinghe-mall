package cn.qinghe.mall.module.pay.controller.internal.refund;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.pay.api.refund.dto.*;
import cn.qinghe.mall.module.pay.service.refund.PayRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

/** 原PayRefundApi委托；仅trade专用凭据，无浏览器金额/成功通知入口。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalPayRefundController {
    public static final String CREATE = "/internal-api/pay/refund/create";
    public static final String RESULT = "/internal-api/pay/refund/result";
    public static final String SUBMIT = "/internal-api/pay/refund/submit";
    public static final String QUERY = "/internal-api/pay/refund/query";
    public static final String RETRY = "/internal-api/pay/refund/retry";
    public static final String CONTINUE = "/internal-api/pay/refund/authorize-continuation";
    public static final String CONTINUATION_RESULT = "/internal-api/pay/refund/continuation-result";
    private final PayRefundService refunds;
    @PostMapping(CREATE)
    public CommonResult<PayRefundResultDTO> create(@Valid @RequestBody PayRefundCreateReqDTO body) {
        return CommonResult.success(refunds.createRefund(body));
    }
    @PostMapping(RESULT)
    public CommonResult<PayRefundResultDTO> result(@Valid @RequestBody PayRefundGetReqDTO body) {
        return CommonResult.success(refunds.getRefund(body));
    }
    @PostMapping(SUBMIT)
    public CommonResult<PayRefundResultDTO> submit(@Valid @RequestBody PayRefundGetReqDTO body) {
        return CommonResult.success(refunds.submitRefund(body));
    }
    @PostMapping(QUERY)
    public CommonResult<PayRefundResultDTO> query(@Valid @RequestBody PayRefundGetReqDTO body) {
        return CommonResult.success(refunds.synchronizeRefund(body));
    }
    @PostMapping(RETRY)
    public CommonResult<PayRefundResultDTO> retry(@Valid @RequestBody PayRefundGetReqDTO body) {
        return CommonResult.success(refunds.retryRefund(body));
    }
    @PostMapping(CONTINUE)
    public CommonResult<PayRefundResultDTO> authorizeContinuation(@Valid @RequestBody PayRefundContinuationReqDTO body) {
        return CommonResult.success(refunds.authorizeContinuation(body));
    }
    @PostMapping(CONTINUATION_RESULT)
    public CommonResult<PayRefundContinuationResultDTO> continuationResult(@Valid @RequestBody PayRefundContinuationGetReqDTO body) {
        return CommonResult.success(refunds.continuationResult(body));
    }
}
