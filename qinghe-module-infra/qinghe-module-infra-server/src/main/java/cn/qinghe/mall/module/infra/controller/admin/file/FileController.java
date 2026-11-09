package cn.qinghe.mall.module.infra.controller.admin.file;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.qinghe.mall.module.infra.controller.admin.file.vo.file.FileRespVO;
import cn.qinghe.mall.module.infra.controller.admin.file.vo.file.FileUploadReqVO;
import cn.qinghe.mall.module.infra.dal.dataobject.file.FileDO;
import cn.qinghe.mall.module.infra.enums.FilePurposeEnum;
import cn.qinghe.mall.module.infra.framework.file.config.FileUploadProperties;
import cn.qinghe.mall.module.infra.framework.file.core.utils.FileResponseUtils;
import cn.qinghe.mall.module.infra.service.file.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.infra.enums.ErrorCodeConstants.FILE_SIZE_EXCEEDED;

/**
 * 取自yudao FileController：保留multipart上传、详情、分页、删除。
 * 本项目两端精确路径、显式上传权限、可信租户/本人文件、私有内容单独读取。
 *
 * @author 芋道源码
 */
@RestController
@RequestMapping({"/admin-api/infra/file", "/platform-api/infra/file"})
@Validated
public class FileController {
    private final FileService fileService;
    private final FileUploadProperties properties;

    public FileController(FileService fileService, FileUploadProperties properties) {
        this.fileService = fileService;
        this.properties = properties;
    }

    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermission('infra:file:create')")
    public CommonResult<FileRespVO> uploadFile(@Valid FileUploadReqVO body,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control", "no-store");
        MultipartFile file = body.getFile();
        if (file.getSize() > properties.getMaxBytes()) {
            throw exception(FILE_SIZE_EXCEEDED);
        }
        FileDO saved = fileService.createFile(file.getBytes(), file.getOriginalFilename(),
                FilePurposeEnum.fromValue(body.getPurpose()));
        return CommonResult.success(FileRespVO.from(saved, prefix(request)));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<FileRespVO> getFile(@RequestParam("id") @Min(1) Long id,
            HttpServletRequest request, HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(FileRespVO.from(fileService.getFile(id), prefix(request)));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<PageResult<FileRespVO>> getFilePage(@Valid FilePageReqVO body,
            HttpServletRequest request, HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control", "no-store");
        PageResult<FileDO> page = fileService.getFilePage(body);
        return CommonResult.success(new PageResult<>(page.getTotal(), page.getList().stream()
                .map(file -> FileRespVO.from(file, prefix(request))).collect(Collectors.toList())));
    }

    @GetMapping("/content")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public ResponseEntity<byte[]> getFileContent(@RequestParam("id") @Min(1) Long id,
                                                HttpServletRequest request) throws Exception {
        SecurityFrameworkUtils.requireBackendRealm(request);
        return FileResponseUtils.attachment(fileService.getFileContent(id));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> deleteFile(@RequestParam("id") @Min(1) Long id,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control", "no-store");
        fileService.deleteFile(id);
        return CommonResult.success(true);
    }

    private String prefix(HttpServletRequest request) {
        return request.getServletPath().startsWith("/platform-api/") ? "/platform-api" : "/admin-api";
    }
}
