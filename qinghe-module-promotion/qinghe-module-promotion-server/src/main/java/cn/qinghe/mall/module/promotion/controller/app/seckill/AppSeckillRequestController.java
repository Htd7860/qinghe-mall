package cn.qinghe.mall.module.promotion.controller.app.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.promotion.controller.app.seckill.vo.AppSeckillAdmissionReqVO;
import cn.qinghe.mall.module.promotion.service.seckill.*;
import cn.qinghe.mall.module.promotion.service.seckill.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 委托原持久受理/本人结果白名单；受理不等于已建单，GET不推进任何业务。 */
@RestController @Validated
@RequestMapping("/app-api/promotion/seckill")
@PreAuthorize("isAuthenticated()")
public class AppSeckillRequestController {
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private final SeckillAdmissionService admission;
    private final SeckillRequestReceiptService receipts;
    private final boolean enabled;
    public AppSeckillRequestController(SeckillAdmissionService admission,SeckillRequestReceiptService receipts,
            @Value("${qinghe.promotion.seckill-results.enabled:false}") boolean enabled) {
        this.admission=admission;this.receipts=receipts;this.enabled=enabled;
    }
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/admit")
    public CommonResult<SeckillAdmissionAttemptDTO> admit(@Valid @RequestBody AppSeckillAdmissionReqVO body,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        if(!enabled)throw exception0(503,"秒杀购买尚未开放");
        return CommonResult.success(admission.attempt(request,store,body.getClientRequestId(),body.getQuoteId(),body.getQuoteDigest()));
    }
    @GetMapping("/request-result")
    public CommonResult<SeckillRequestReceiptDTO> byClient(@RequestParam("clientRequestId") @Pattern(regexp=UUID) String client,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(receipts.byClient(request,store,client));
    }
    @GetMapping("/request-detail")
    public CommonResult<SeckillRequestReceiptDTO> byRequest(@RequestParam("requestNo") @Pattern(regexp=UUID) String number,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(receipts.byRequest(request,store,number));
    }
    @GetMapping("/request-page")
    public CommonResult<PageResult<SeckillRequestReceiptDTO>> page(@Valid PageParam query,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,HttpServletRequest request) {
        return CommonResult.success(receipts.page(request,store,query));
    }
}
