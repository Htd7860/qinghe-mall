package cn.qinghe.mall.module.system.controller.admin.tenant;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.service.management.ManagementTransactions;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.*;
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages.*;
import cn.qinghe.mall.module.system.service.management.PackageManagementService;
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/platform-api/system/tenant-package")
public class PackageManagementController {
    private final PackageManagementService service;
    private final cn.qinghe.mall.module.system.service.management.ManagementMenuCatalog catalog;
    @GetMapping("/menu-options")
    @PreAuthorize("@ss.hasAnyPermissions('system:tenant-package:create','system:tenant-package:update')")
    public CommonResult<List<cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO>> menuOptions(
            HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(catalog.merchantOptions());
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:tenant-package:query')")
    public CommonResult<PageResult<PackageRespVO>> page(@Valid PackagePageReqVO query,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.page(query));
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:tenant-package:query')")
    public CommonResult<PackageRespVO> get(@RequestParam("id") @Min(1) Long id,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.get(id));
    }
    @GetMapping("/change-result") @PreAuthorize("@ss.hasPermission('system:tenant-package:update')")
    public CommonResult<PackageChangeRespVO> result(@RequestParam("operationId") String operationId,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.getChange(operationId));
    }
    @GetMapping("/change-history") @PreAuthorize("@ss.hasPermission('system:tenant-package:query')")
    public CommonResult<List<PackageChangeRespVO>> history(@RequestParam("packageId") @Min(1) Long packageId,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.history(packageId));
    }
    @PostMapping("/preview-change") @PreAuthorize("@ss.hasPermission('system:tenant-package:update')")
    public CommonResult<PackageImpactRespVO> preview(@Valid @RequestBody PackagePreviewReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.preview(body));
    }
    @PostMapping("/update") @PreAuthorize("@ss.hasPermission('system:tenant-package:update')")
    public CommonResult<PackageChangeRespVO> update(@Valid @RequestBody PackageSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.update(body));
    }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:tenant-package:create')")
    public CommonResult<Long> create(@Valid @RequestBody PackageSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.create(body));
    }
    @PostMapping("/update-status") @PreAuthorize("@ss.hasPermission('system:tenant-package:update')")
    public CommonResult<Long> status(@Valid @RequestBody VersionedStatusReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.status(body));
    }
    @PostMapping("/delete") @PreAuthorize("@ss.hasPermission('system:tenant-package:delete')")
    public CommonResult<Long> delete(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.delete(body));
    }
    @PostMapping("/restore") @PreAuthorize("@ss.hasPermission('system:tenant-package:restore')")
    public CommonResult<Long> restore(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.restore(body));
    }
    private void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);ManagementTransactions.requirePlatformRead();
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
    }
}
