package cn.qinghe.mall.module.trade.controller.app.aftersale;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.*;
import cn.qinghe.mall.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeDeliveryExpressRespVO;
import cn.qinghe.mall.module.trade.service.aftersale.*;
import cn.qinghe.mall.module.trade.service.aftersale.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/** 择取原App本人分页/详情/申请/寄回/撤销职责；既有Service核对实际Host和本人归属。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/app-api/trade/after-sale")
public class AppAfterSaleController {
    private final AfterSaleQueryService queries;
    private final AfterSaleApplicationService applications;
    private final AfterSaleDecisionService decisions;
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/page")
    public CommonResult<PageResult<AfterSaleSummaryRespVO>> page(@Valid AfterSalePageReqVO body,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberPage(request,store,body));}
    @GetMapping("/get")
    public CommonResult<AfterSaleDetailRespVO> detail(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberDetail(request,store,id));}
    @GetMapping("/log-page")
    public CommonResult<PageResult<AfterSaleLogRespVO>> logs(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@Valid PageParam body,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberLogs(request,store,id,body));}
    @GetMapping("/return-address")
    public CommonResult<AfterSaleReturnAddress> address(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberReturnAddress(request,store,id));}
    @GetMapping("/application-result")
    public CommonResult<AfterSaleSummaryRespVO> applicationResult(@RequestParam("requestId") String requestId,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberApplicationResult(request,store,requestId));}
    @GetMapping("/decision-result")
    public CommonResult<AfterSaleDecisionResult> decisionResult(@RequestParam("requestId") String requestId,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberDecisionResult(request,store,requestId));}
    @GetMapping("/express-options")
    public CommonResult<List<TradeDeliveryExpressRespVO>> carriers(@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(queries.memberCarriers(request,store));}
    @PostMapping("/create")
    public CommonResult<AfterSaleApplyResult> apply(@Valid @RequestBody AppAfterSaleCreateReqVO body,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(applications.apply(request,store,body));}
    @PostMapping("/delivery")
    public CommonResult<AfterSaleDecisionResult> deliver(@Valid @RequestBody AfterSaleReturnDeliveryCommand body,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(decisions.deliver(request,store,body));}
    @PostMapping("/cancel")
    public CommonResult<AfterSaleDecisionResult> cancel(@Valid @RequestBody AfterSaleDecisionCommand body,@RequestParam(value="store",required=false) String store,HttpServletRequest request){return CommonResult.success(decisions.cancel(request,store,body));}
}
