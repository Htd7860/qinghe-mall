package cn.qinghe.mall.module.system.controller.admin.user;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
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
import cn.qinghe.mall.module.system.controller.admin.user.vo.*;
import cn.qinghe.mall.module.system.service.management.UserManagementService;
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping({"/admin-api/system/user", "/platform-api/system/user"})
public class UserManagementController {
    private final UserManagementService service;
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<PageResult<UserRespVO>> page(@Valid UserPageReqVO query, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.page(query));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<UserRespVO> get(@RequestParam("id") @Min(1) Long id, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.get(id));
    }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('system:user:create')")
    public CommonResult<Long> create(@Valid @RequestBody UserSaveReqVO body, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.create(body));
    }
    @PostMapping("/update")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public CommonResult<Long> update(@Valid @RequestBody UserSaveReqVO body, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.update(body));
    }
    @PostMapping("/update-status")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public CommonResult<Long> status(@Valid @RequestBody VersionedStatusReqVO body, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.status(body));
    }
    @PostMapping("/update-password")
    @PreAuthorize("@ss.hasPermission('system:user:update-password')")
    public CommonResult<Long> resetPassword(@Valid @RequestBody UserPasswordReqVO body, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.resetPassword(body));
    }
    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Long> delete(@Valid @RequestBody VersionedIdReqVO body, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.delete(body));
    }
    @PostMapping("/restore")
    @PreAuthorize("@ss.hasPermission('system:user:restore')")
    public CommonResult<Long> restore(@Valid @RequestBody VersionedIdReqVO body, HttpServletRequest request, HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.restore(body));
    }
    private void prepare(HttpServletRequest request, HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
