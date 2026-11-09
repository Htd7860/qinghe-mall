package cn.qinghe.mall.module.infra.controller.internal.file;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindReqDTO;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindRespDTO;
import cn.qinghe.mall.module.infra.service.file.FileBusinessService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/** 原版FileApi的内部服务职责；本项目新增按真实Bearer核验图片并登记引用。网关不外部路由。 */
@RestController
public class InternalFileController {
    private final FileBusinessService service;
    public InternalFileController(FileBusinessService service) { this.service = service; }

    @PostMapping("/internal-api/infra/file/bind")
    public CommonResult<FileBusinessBindRespDTO> bind(@Valid @RequestBody FileBusinessBindReqDTO body,
                                                     HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(service.bind(body));
    }
}
