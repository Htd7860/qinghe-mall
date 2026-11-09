package cn.qinghe.mall.module.infra.controller.app.file;

import cn.qinghe.mall.module.infra.framework.file.core.utils.FileResponseUtils;
import cn.qinghe.mall.module.infra.service.file.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.security.PermitAll;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

/** 沿用yudao匿名图片读取入口；固定生成路径结构，仍必须经过真实元数据白名单。 */
@RestController
@Validated
public class PublicFileController {
    private final FileService fileService;

    public PublicFileController(FileService fileService) { this.fileService = fileService; }

    @PermitAll
    @GetMapping("/public-api/infra/file/{configId}/get/tenant/{tenantId}/{purpose}/{date}/{filename}")
    public ResponseEntity<byte[]> getFileContent(
            @PathVariable("configId") @Min(1) Long configId,
            @PathVariable("tenantId") @Min(0) Long tenantId,
            @PathVariable("purpose") @Pattern(regexp = "product-image|store-logo") String purpose,
            @PathVariable("date") @Pattern(regexp = "[0-9]{8}") String date,
            @PathVariable("filename") @Pattern(regexp = "[a-f0-9]{32}\\.(jpg|jpeg|png|gif|webp|bmp)") String filename)
            throws Exception {
        String path = "tenant/" + tenantId + "/" + purpose + "/" + date + "/" + filename;
        return FileResponseUtils.image(fileService.getPublicFileContent(configId, path));
    }
}
