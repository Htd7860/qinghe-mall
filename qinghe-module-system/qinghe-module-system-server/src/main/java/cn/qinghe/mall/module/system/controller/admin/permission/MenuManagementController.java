package cn.qinghe.mall.module.system.controller.admin.permission;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.*;
import cn.qinghe.mall.module.system.service.management.MenuManagementService;
import cn.qinghe.mall.module.system.service.management.ManagementTransactions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import java.util.List;
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/platform-api/system/menu")
public class MenuManagementController {
    private final MenuManagementService service;
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('system:menu:query')")
    public CommonResult<List<MenuRespVO>> list(@RequestParam("realm") @Pattern(regexp="merchant|platform") String realm,
            @RequestParam(value="deleted",defaultValue="false") boolean deleted,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.list(realm,deleted));
    }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:menu:create')")
    public CommonResult<Long> create(@Valid @RequestBody MenuSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.create(body));
    }
    @PostMapping("/update") @PreAuthorize("@ss.hasPermission('system:menu:update')")
    public CommonResult<Long> update(@Valid @RequestBody MenuSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.update(body));
    }
    @PostMapping("/update-status") @PreAuthorize("@ss.hasPermission('system:menu:update')")
    public CommonResult<Long> status(@Valid @RequestBody VersionedStatusReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.status(body));
    }
    @PostMapping("/delete") @PreAuthorize("@ss.hasPermission('system:menu:delete')")
    public CommonResult<Long> delete(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.delete(body));
    }
    @PostMapping("/restore") @PreAuthorize("@ss.hasPermission('system:menu:restore')")
    public CommonResult<Long> restore(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);return CommonResult.success(service.restore(body));
    }
    private void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);ManagementTransactions.requirePlatformRead();
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
    }
}
