package cn.qinghe.mall.module.promotion.controller.internal.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillCheckoutContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;

/** 原SeckillActivityApi只读委托；保留原会员Bearer，网关不路由internal-api。 @author HUIHUI */
@RestController @RequiredArgsConstructor
public class InternalSeckillCheckoutController {
    private final SeckillCheckoutContextService service;
    @GetMapping("/internal-api/promotion/seckill/checkout-context")
    public CommonResult<SeckillCheckoutContextDTO> current(@RequestParam Long activityId,@RequestParam Integer generation,
            @RequestParam Long skuId,@RequestParam Integer quantity,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try {
            return CommonResult.success(service.current(activityId,generation,skuId,quantity));
        } catch (ServiceException rejected) {
            // The internal RPC client reads business refusals from CommonResult.
            // Non-2xx transport responses are treated as dependency failures.
            if (Integer.valueOf(400).equals(rejected.getCode()) || Integer.valueOf(409).equals(rejected.getCode())) {
                response.setStatus(200);
                return CommonResult.error(rejected.getCode(),rejected.getMessage());
            }
            throw rejected;
        }
    }
}
