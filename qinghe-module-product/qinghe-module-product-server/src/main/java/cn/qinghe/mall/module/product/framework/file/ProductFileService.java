package cn.qinghe.mall.module.product.framework.file;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindReqDTO;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.net.URI;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 规范URL来自infra真实元数据；登记成功后再提交本地资料，不对未知结果盲目解绑定。 */
@Service
public class ProductFileService {
    private final ProductFileFeignApi api;
    private final SecurityProperties securityProperties;
    private final Semaphore calls = new Semaphore(32);

    public ProductFileService(ProductFileFeignApi api, SecurityProperties securityProperties,
            @org.springframework.beans.factory.annotation.Value("${qinghe.product.infra-base-url:http://127.0.0.1:8081}") String baseUrl) {
        URI base = URI.create(baseUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("infra文件服务地址配置不正确");
        }
        this.api = api; this.securityProperties = securityProperties;
    }

    public String bind(String type, Long id, String url) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            throw exception0(503, "图片关联缺少当前认证请求");
        }
        String token = SecurityFrameworkUtils.obtainAuthorization(
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest(), securityProperties.getTokenHeader());
        if (token == null) { throw exception0(401, "请先登录"); }
        FileBusinessBindReqDTO body = new FileBusinessBindReqDTO();
        body.setResourceType(type); body.setResourceId(id); body.setUrl(url);
        if (!calls.tryAcquire()) { throw exception0(503, "图片服务繁忙，请稍后重试"); }
        try {
            CommonResult<FileBusinessBindRespDTO> result = api.bind("Bearer " + token, body);
            if (result == null || result.getCode() == null) { throw exception0(503, "图片关联服务暂不可用"); }
            result.checkError();
            if (result.getData() == null || result.getData().getId() == null || result.getData().getId() <= 0
                    || !url.equals(result.getData().getUrl())) { throw exception0(503, "图片关联结果异常"); }
            return result.getData().getUrl();
        } finally { calls.release(); }
    }
}
