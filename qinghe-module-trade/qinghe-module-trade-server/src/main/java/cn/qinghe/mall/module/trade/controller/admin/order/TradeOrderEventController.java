package cn.qinghe.mall.module.trade.controller.admin.order;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.service.message.TradeOrderEventAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;

/** 择取原PayNotifyController通知任务分页/详情委托；无通用消息管理控制台。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/admin-api/trade/order-event")
public class TradeOrderEventController {
    private final TradeOrderEventAdminService service;
    @ModelAttribute
    public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<PageResult<TradeEventRespVO>> page(@Valid TradeEventPageReqVO body){return CommonResult.success(service.page(body));}
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<TradeEventRespVO> get(@RequestParam("eventId") String eventId){return CommonResult.success(service.get(eventId));}
    @GetMapping("/replay-result") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<TradeEventReplayRespVO> result(@RequestParam("requestId") String requestId){return CommonResult.success(service.replayResult(requestId));}
    @GetMapping("/replay-history") @PreAuthorize("@ss.hasPermission('trade:order-recovery:query')")
    public CommonResult<PageResult<TradeEventReplayRespVO>> history(@RequestParam("eventId") String eventId,@Valid PageParam page){return CommonResult.success(service.history(eventId,page));}
    @PostMapping("/replay") @PreAuthorize("@ss.hasPermission('trade:order-recovery:replay')")
    public CommonResult<TradeEventReplayRespVO> replay(@Valid @RequestBody TradeEventReplayReqVO body){return CommonResult.success(service.replay(body));}
}
