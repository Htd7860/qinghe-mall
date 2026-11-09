package cn.qinghe.mall.module.trade.framework.order;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.pay.api.order.dto.*;
import cn.qinghe.mall.module.pay.api.refund.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 原PayOrderApi委托，沿用已有有限超时/无自动重试配置。
 * @author 芋道源码
 */
@FeignClient(name="qinghe-order-pay",contextId="qingheOrderPay",url="${qinghe.trade.pay-base-url:http://127.0.0.1:8086}",configuration=OrderResourceFeignConfiguration.class)
public interface OrderPayFeignApi {
    @PostMapping("/internal-api/pay/refund/continuation-result")
    CommonResult<PayRefundContinuationResultDTO> refundContinuationResult(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundContinuationGetReqDTO body);
    @PostMapping("/internal-api/pay/refund/authorize-continuation")
    CommonResult<PayRefundResultDTO> authorizeRefundContinuation(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundContinuationReqDTO body);
    @PostMapping("/internal-api/pay/refund/create")
    CommonResult<PayRefundResultDTO> createRefund(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundCreateReqDTO body);
    @PostMapping("/internal-api/pay/refund/result")
    CommonResult<PayRefundResultDTO> refundResult(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundGetReqDTO body);
    @PostMapping("/internal-api/pay/refund/submit")
    CommonResult<PayRefundResultDTO> submitRefund(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundGetReqDTO body);
    @PostMapping("/internal-api/pay/refund/query")
    CommonResult<PayRefundResultDTO> queryRefund(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundGetReqDTO body);
    @PostMapping("/internal-api/pay/refund/retry")
    CommonResult<PayRefundResultDTO> retryRefund(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayRefundGetReqDTO body);
    @PostMapping("/internal-api/pay/order/recover-qr")
    CommonResult<PayOrderSubmissionRespDTO> recoverQr(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderGetReqDTO body);
    @PostMapping("/internal-api/pay/order/submit")
    CommonResult<PayOrderSubmissionRespDTO> submit(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderGetReqDTO body);
    @PostMapping("/internal-api/pay/order/submission-result")
    CommonResult<PayOrderSubmissionRespDTO> submission(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderGetReqDTO body);
    @PostMapping("/internal-api/pay/order/create")
    CommonResult<PayOrderRespDTO> create(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderCreateReqDTO body);
    @PostMapping("/internal-api/pay/order/result")
    CommonResult<PayOrderRespDTO> result(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderGetReqDTO body);
    @PostMapping("/internal-api/pay/order/close")
    CommonResult<PayOrderRespDTO> close(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderCreateReqDTO body);
    @PostMapping("/internal-api/pay/order/synchronize")
    CommonResult<PayOrderRespDTO> synchronize(@RequestHeader("Authorization") String service,
            @RequestHeader("tenant-id") Long tenant,@RequestBody PayOrderGetReqDTO body);
}
