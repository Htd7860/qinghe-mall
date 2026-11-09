package cn.qinghe.mall.module.product.framework.delivery;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.trade.api.delivery.dto.DeliveryExpressTemplateCheckRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
/** 固定内部模板验证接口，沿用现有无重试/无重定向/无凭据日志配置。 */
@FeignClient(name="qinghe-trade-delivery",contextId="qingheProductDelivery",
 url="${qinghe.product.trade-base-url:http://127.0.0.1:8083}",configuration=SystemSecurityFeignConfiguration.class)
public interface ProductDeliveryFeignApi {
    @GetMapping("/internal-api/trade/delivery-express-template/validate")
    CommonResult<DeliveryExpressTemplateCheckRespDTO> validate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("id") Long id);
}
