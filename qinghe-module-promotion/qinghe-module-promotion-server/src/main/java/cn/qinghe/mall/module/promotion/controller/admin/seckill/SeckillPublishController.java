package cn.qinghe.mall.module.promotion.controller.admin.seckill;

import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.*;
import cn.qinghe.mall.module.promotion.service.seckill.*;
import cn.qinghe.mall.module.promotion.service.seckill.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import javax.servlet.http.*;

/** 原SeckillActivityController映射/权限/委托职责；逐原请求恢复，不返回内部划拨计划。 @author halfninety */
@RestController
@RequestMapping("/admin-api/promotion/seckill-activity")
@Validated
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
public class SeckillPublishController {
    private final SeckillPublishPreparationService preparations;
    private final SeckillPublishStockService stock;
    private final SeckillPublishDecisionService decisions;
    private final SeckillPublishResultService results;
    private final SeckillPublishHistoryService history;

    @ModelAttribute public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @PostMapping("/prepare-publish")
    public CommonResult<SeckillPublishResultRespVO> preparePublish(@Valid @RequestBody SeckillPublishReqDTO body) {
        preparations.prepare(body);return CommonResult.success(results.get(body));
    }
    @PostMapping("/advance-publish-sku")
    public CommonResult<SeckillPublishResultRespVO> advance(@Valid @RequestBody SeckillPublishSkuReqVO body) {
        stock.advance(body,body.getSkuId());return CommonResult.success(results.get(body));
    }
    @PostMapping("/cancel-publish")
    public CommonResult<SeckillPublishResultRespVO> cancel(@Valid @RequestBody SeckillPublishReasonReqVO body) {
        stock.cancelPreparation(body,body.getReason());return CommonResult.success(results.get(body));
    }
    @PostMapping("/compensate-publish-sku")
    public CommonResult<SeckillPublishResultRespVO> compensate(@Valid @RequestBody SeckillPublishSkuReqVO body) {
        stock.compensate(body,body.getSkuId());return CommonResult.success(results.get(body));
    }
    @PostMapping("/confirm-publish")
    public CommonResult<SeckillPublishResultRespVO> publish(@Valid @RequestBody SeckillPublishReqDTO body) {
        decisions.publish(body);return CommonResult.success(results.get(body));
    }
    @PostMapping("/withdraw-publish")
    public CommonResult<SeckillPublishResultRespVO> withdraw(@Valid @RequestBody SeckillPublishReasonReqVO body) {
        decisions.withdrawBeforeStart(body,body.getReason());return CommonResult.success(results.get(body));
    }
    @PostMapping("/settle-publish")
    public CommonResult<SeckillPublishResultRespVO> settle(@Valid @RequestBody SeckillPublishReqDTO body) {
        decisions.settleFailed(body);return CommonResult.success(results.get(body));
    }
    @PostMapping("/reopen-draft")
    public CommonResult<SeckillPublishResultRespVO> reopen(@Valid @RequestBody SeckillPublishReqDTO body) {
        decisions.reopenSettled(body);return CommonResult.success(results.get(body));
    }
    @GetMapping("/publish-result")
    public CommonResult<SeckillPublishResultRespVO> result(@Valid SeckillPublishReqDTO query) {
        return CommonResult.success(results.get(query));
    }
    @GetMapping("/publish-history")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query')")
    public CommonResult<PageResult<SeckillPublishHistoryRespVO>> history(
            @RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@Valid PageParam page) {
        PageResult<SeckillPublishHistoryDTO> result=history.page(id,page);
        return CommonResult.success(new PageResult<>(result.getTotal(),result.getList().stream()
                .map(SeckillPublishHistoryRespVO::from).collect(java.util.stream.Collectors.toList())));
    }
}
