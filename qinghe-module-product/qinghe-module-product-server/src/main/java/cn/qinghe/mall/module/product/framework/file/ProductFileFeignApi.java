package cn.qinghe.mall.module.product.framework.file;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindReqDTO;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 沿用yudao内部FileApi的Feign形式，复用现有有界超时/无重试/无凭据日志配置。 */
@FeignClient(name = "qinghe-infra-file", contextId = "qingheProductFile",
        url = "${qinghe.product.infra-base-url:http://127.0.0.1:8081}",
        configuration = SystemSecurityFeignConfiguration.class)
public interface ProductFileFeignApi {
    @PostMapping("/internal-api/infra/file/bind")
    CommonResult<FileBusinessBindRespDTO> bind(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                              @RequestBody FileBusinessBindReqDTO body);
}
