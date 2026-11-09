package cn.qinghe.mall.module.trade.controller.admin.order;

import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.service.order.TradeOrderAdminQueryService;
import cn.qinghe.mall.module.trade.service.order.TradeOrderFulfillmentService;
import cn.qinghe.mall.module.trade.service.order.TradeOperatingStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/** 择取原后台订单分页/详情/发货委托；权限在被调用的服务代理/既有履约入口检查。
 * 不提供替消费者收货、调价、任意地址覆盖、跨租户查询或导出。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/admin-api/trade/order")
public class TradeOrderController {
    private final TradeOrderAdminQueryService queries;
    private final TradeOrderFulfillmentService fulfillment;
    private final TradeOperatingStatisticsService statistics;
    @ModelAttribute public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/page")
    public CommonResult<PageResult<TradeOrderPageItemRespVO>> page(@Valid TradeOrderPageReqVO body){return CommonResult.success(queries.page(body));}
    @GetMapping("/summary")
    public CommonResult<TradeOperatingSummaryRespVO> summary(@Valid TradeOperatingSummaryReqVO body){return CommonResult.success(statistics.summary(body));}
    @GetMapping("/get-detail")
    public CommonResult<TradeOrderDetailRespVO> detail(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id){return CommonResult.success(queries.detail(id));}
    @GetMapping("/get-delivery-detail")
    public CommonResult<TradeOrderDeliveryDetailRespVO> deliveryDetail(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id){return CommonResult.success(queries.deliveryDetail(id));}
    @GetMapping("/log-page")
    public CommonResult<PageResult<TradeOrderLogRespVO>> logs(@RequestParam("id") @Min(1) @Max(9007199254740991L) Long id,@Valid PageParam body){return CommonResult.success(queries.logs(id,body));}
    @GetMapping("/express-options")
    public CommonResult<List<TradeDeliveryExpressRespVO>> carriers(){return CommonResult.success(BeanUtils.toBean(fulfillment.carrierOptions(),TradeDeliveryExpressRespVO.class));}
    @PostMapping("/delivery")
    public CommonResult<Boolean> delivery(@Valid @RequestBody TradeOrderDeliveryReqVO body){fulfillment.deliver(body);return CommonResult.success(true);}
    @PostMapping("/update-logistics")
    public CommonResult<Boolean> correct(@Valid @RequestBody TradeOrderLogisticsUpdateReqVO body){fulfillment.correctLogistics(body);return CommonResult.success(true);}
}
