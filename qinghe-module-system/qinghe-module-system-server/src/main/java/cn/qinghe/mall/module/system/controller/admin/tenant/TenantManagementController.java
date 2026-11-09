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
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.*;
import cn.qinghe.mall.module.system.service.management.TenantManagementService;
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/platform-api/system/tenant")
public class TenantManagementController {
    private final TenantManagementService service;
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<PageResult<TenantRespVO>> page(@Valid TenantPageReqVO query,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.page(query));
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<TenantRespVO> get(@RequestParam("id") @Min(1) Long id,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.get(id));
    }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:tenant:create')")
    public CommonResult<Long> create(@Valid @RequestBody TenantSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.create(body));
    }
    @PostMapping("/update") @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<Long> update(@Valid @RequestBody TenantSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.update(body));
    }
    @PostMapping("/update-status") @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<Long> status(@Valid @RequestBody VersionedStatusReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.status(body));
    }
    @PostMapping("/delete") @PreAuthorize("@ss.hasPermission('system:tenant:delete')")
    public CommonResult<Long> delete(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.delete(body));
    }
    @PostMapping("/restore") @PreAuthorize("@ss.hasPermission('system:tenant:restore')")
    public CommonResult<Long> restore(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.restore(body));
    }
    private void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);ManagementTransactions.requirePlatformRead();
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
    }
}
