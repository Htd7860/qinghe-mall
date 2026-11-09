package cn.qinghe.mall.module.trade.controller.internal.delivery;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.trade.api.delivery.dto.DeliveryExpressTemplateCheckRespDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;
import cn.qinghe.mall.module.trade.service.delivery.DeliveryExpressTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Min;
/** 当前Bearer校验真实同租户模板，Gateway不提供外部路由。 */
@RestController
@Validated
public class InternalDeliveryExpressTemplateController {
    private final DeliveryExpressTemplateService service;
    public InternalDeliveryExpressTemplateController(DeliveryExpressTemplateService service) { this.service=service; }
    @GetMapping("/internal-api/trade/delivery-express-template/validate")
    @PreAuthorize("@ss.hasAnyPermissions('product:spu:create','product:spu:update')")
    public CommonResult<DeliveryExpressTemplateCheckRespDTO> validate(@RequestParam("id") @Min(1) Long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        DeliveryExpressTemplateDO template=service.validateDeliveryExpressTemplate(id);
        DeliveryExpressTemplateCheckRespDTO result=new DeliveryExpressTemplateCheckRespDTO();
        result.setId(template.getId());result.setTenantId(template.getTenantId());result.setAuthVersion(template.getAuthVersion());
        result.setChargeMode(template.getChargeMode());return CommonResult.success(result);
    }
}
