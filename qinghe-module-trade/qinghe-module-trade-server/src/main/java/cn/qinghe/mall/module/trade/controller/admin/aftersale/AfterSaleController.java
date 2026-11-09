package cn.qinghe.mall.module.trade.controller.admin.aftersale;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.*;
import cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate.DeliveryAreaOptionRespVO;
import cn.qinghe.mall.module.trade.service.aftersale.*;
import cn.qinghe.mall.module.trade.service.aftersale.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/** 择取原后台售后分页/详情/审核/收货委托；权限在服务代理/既有决定入口检查。
 * 财务只引用原退款计划；不接受浏览器资金成功回调。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/admin-api/trade/after-sale")
public class AfterSaleController {
    private final AfterSaleQueryService queries;
    private final AfterSaleDecisionService decisions;
    private final AfterSaleRefundPrepareService refundPreparation;
    private final AfterSaleRefundAdminService refunds;
    @ModelAttribute public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/page")
    public CommonResult<PageResult<AfterSaleSummaryRespVO>> page(@Valid AfterSalePageReqVO body){return CommonResult.success(queries.merchantPage(body));}
    @GetMapping("/area-options")
    public CommonResult<List<DeliveryAreaOptionRespVO>> areas(){return CommonResult.success(queries.merchantAreas());}
    @GetMapping("/get-detail")
    public CommonResult<AfterSaleDetailRespVO> detail(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id){return CommonResult.success(queries.merchantDetail(id));}
    @GetMapping("/log-page")
    public CommonResult<PageResult<AfterSaleLogRespVO>> logs(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@Valid PageParam body){return CommonResult.success(queries.merchantLogs(id,body));}
    @GetMapping("/return-address")
    public CommonResult<AfterSaleReturnAddress> address(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id){return CommonResult.success(queries.merchantReturnAddress(id));}
    @GetMapping("/decision-result")
    public CommonResult<AfterSaleDecisionResult> result(@RequestParam("requestId") String requestId){return CommonResult.success(queries.merchantDecisionResult(requestId));}
    @PostMapping("/agree")
    public CommonResult<AfterSaleDecisionResult> approve(@Valid @RequestBody AfterSaleDecisionCommand body){return CommonResult.success(decisions.approve(body));}
    @PostMapping("/disagree")
    public CommonResult<AfterSaleDecisionResult> reject(@Valid @RequestBody AfterSaleDecisionCommand body){return CommonResult.success(decisions.reject(body));}
    @PostMapping("/receive")
    public CommonResult<AfterSaleDecisionResult> inspect(@Valid @RequestBody AfterSaleInspectionCommand body){return CommonResult.success(decisions.inspect(body));}

    @PostMapping("/refund-prepare")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundPlanDTO> prepareRefund(@Valid @RequestBody AfterSaleRefundPrepareCommand body) {
        return CommonResult.success(refundPreparation.prepare(body));
    }

    @PostMapping("/refund-execute")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundRespVO> executeRefund(@Valid @RequestBody AfterSaleRefundCommandReqVO body) {
        return CommonResult.success(refunds.execute(body));
    }

    @PostMapping("/refund-sync")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundRespVO> synchronizeRefund(@Valid @RequestBody AfterSaleRefundCommandReqVO body) {
        return CommonResult.success(refunds.synchronize(body));
    }

    @GetMapping("/refund-result")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundRespVO> refundResult(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,
            @RequestParam(value="refundNo",required=false) @Pattern(regexp="RF[0-9a-f]{32}") String refundNo) {
        return CommonResult.success(refunds.result(id,refundNo));
    }

    @PostMapping("/refund-retry")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundRespVO> retryRefund(@Valid @RequestBody AfterSaleRefundRetryReqVO body) {
        return CommonResult.success(refunds.retry(body));
    }

    @GetMapping("/refund-retry-result")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundRetryResultRespVO> refundRetryResult(@RequestParam("requestId") String requestId) {
        return CommonResult.success(refunds.retryResult(requestId));
    }

    @GetMapping("/refund-budget")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundBudgetRespVO> refundBudget(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,
            @RequestParam("refundNo") @Pattern(regexp="RF[0-9a-f]{32}") String refundNo) {
        return CommonResult.success(refunds.budget(id,refundNo));
    }

    @PostMapping("/refund-continue")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundBudgetRespVO> continueRefund(@Valid @RequestBody AfterSaleRefundContinuationReqVO body) {
        return CommonResult.success(refunds.continueRefund(body));
    }
    @GetMapping("/refund-continue-result")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundContinuationResultRespVO> refundContinuationResult(@RequestParam("requestId") String requestId) {
        return CommonResult.success(refunds.continuationResult(requestId));
    }

    @GetMapping("/refund-prepare-result")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<AfterSaleRefundRespVO> refundPreparationResult(@RequestParam("requestId") String requestId) {
        return CommonResult.success(refunds.preparationResult(requestId));
    }
}
