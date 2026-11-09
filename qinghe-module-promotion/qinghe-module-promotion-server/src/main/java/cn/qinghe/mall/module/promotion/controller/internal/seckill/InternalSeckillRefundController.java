package cn.qinghe.mall.module.promotion.controller.internal.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillRefundResourceService;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillRefundCommandContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;

@RestController @RequiredArgsConstructor @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class InternalSeckillRefundController {
    private final SeckillRefundResourceService service;
    @PostMapping(SeckillRefundCommandContext.RECOVER)
    public CommonResult<SeckillRefundResourceReceiptDTO> recover(@RequestBody SeckillRefundResourceLookupDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.recover(body));
    }
    @PostMapping(SeckillRefundCommandContext.RESULT)
    public CommonResult<SeckillRefundResourceReceiptDTO> result(@RequestBody SeckillRefundResourceLookupDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return CommonResult.success(service.get(body));
    }
}
