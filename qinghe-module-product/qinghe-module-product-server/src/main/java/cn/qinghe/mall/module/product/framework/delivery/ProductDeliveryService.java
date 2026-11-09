package cn.qinghe.mall.module.product.framework.delivery;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.qinghe.mall.module.trade.api.delivery.dto.DeliveryExpressTemplateCheckRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 真实模板验证；不把通过验证等同于具体收货地址已匹配或运费已算出。 */
@Service
public class ProductDeliveryService {
    private final ProductDeliveryFeignApi api;
    private final SecurityProperties security;
    private final Semaphore calls=new Semaphore(32);
    public ProductDeliveryService(ProductDeliveryFeignApi api,SecurityProperties security,
            @Value("${qinghe.product.trade-base-url:http://127.0.0.1:8083}") String baseUrl) {
        URI uri=URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) { throw new IllegalArgumentException("交易服务地址配置不正确"); }
        this.api=api;this.security=security;
    }
    public DeliveryExpressTemplateCheckRespDTO validate(Long id,List<ProductSkuSaveReqVO> skus) {
        if (id==null || id<=0 || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            throw exception0(503,"配送验证缺少当前认证请求或模板编号");
        }
        String token=SecurityFrameworkUtils.obtainAuthorization(((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest(),security.getTokenHeader());
        if (token==null) { throw exception0(401,"请先登录"); }
        if (!calls.tryAcquire()) { throw exception0(503,"配送服务繁忙，请稍后重试"); }
        try {
            CommonResult<DeliveryExpressTemplateCheckRespDTO> result=api.validate("Bearer "+token,id);
            if (result==null || result.getCode()==null) { throw exception0(503,"配送服务暂不可用"); }
            result.checkError();DeliveryExpressTemplateCheckRespDTO template=result.getData();
            if (template==null || !Objects.equals(template.getId(),id)
                    || !Objects.equals(template.getTenantId(),TenantContextHolder.getRequiredTenantId())
                    || template.getAuthVersion()==null || template.getAuthVersion()<0
                    || !(Integer.valueOf(1).equals(template.getChargeMode()) || Integer.valueOf(2).equals(template.getChargeMode()))) {
                throw exception0(503,"配送模板验证结果不完整");
            }
            if (template.getChargeMode()==2 && (skus==null || skus.stream().filter(sku -> Integer.valueOf(0).equals(sku.getStatus()))
                    .anyMatch(sku -> sku.getWeight()==null || sku.getWeight().signum()<=0))) {
                throw exception0(400,"按重量配送的启用SKU必须填写有效重量");
            }
            return template;
        } finally { calls.release(); }
    }
}
