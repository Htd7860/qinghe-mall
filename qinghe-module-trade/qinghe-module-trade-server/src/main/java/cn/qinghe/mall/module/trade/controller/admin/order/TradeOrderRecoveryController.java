package cn.qinghe.mall.module.trade.controller.admin.order;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.service.order.TradeOrderRecoveryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;

/** 择取原TradeOrderController的权限分页/详情委托；本批无独立运维页面。
 * @author 芋道源码
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin-api/trade/order-recovery")
public class TradeOrderRecoveryController {
    private final TradeOrderRecoveryAdminService service;
    @ModelAttribute
    public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<PageResult<TradeRecoveryRespVO>> page(@Valid TradeRecoveryPageReqVO body){return CommonResult.success(service.page(body));}
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<TradeRecoveryRespVO> get(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id){return CommonResult.success(service.get(id));}
    @GetMapping("/replay-result") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<TradeRecoveryReplayRespVO> result(@RequestParam("requestId") String requestId){return CommonResult.success(service.replayResult(requestId));}
    @GetMapping("/replay-history") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<PageResult<TradeRecoveryReplayRespVO>> history(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@Valid PageParam page){return CommonResult.success(service.history(id,page));}
    @PostMapping("/replay") @PreAuthorize("@ss.hasPermission('trade:order-recovery:replay')")
    public CommonResult<TradeRecoveryReplayRespVO> replay(@Valid @RequestBody TradeRecoveryReplayReqVO body){return CommonResult.success(service.replay(body));}
}
