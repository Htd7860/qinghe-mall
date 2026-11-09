package cn.qinghe.mall.module.system.controller.admin.dept;
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
import cn.qinghe.mall.module.system.controller.admin.dept.vo.*;
import cn.qinghe.mall.module.system.service.management.DeptManagementService;
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping({"/admin-api/system/dept", "/platform-api/system/dept"})
public class DeptManagementController {
    private final DeptManagementService service;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<List<DeptRespVO>> list(@RequestParam(value="deleted",defaultValue="false") boolean deleted,
            HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.list(deleted));
    }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('system:dept:create')")
    public CommonResult<Long> create(@Valid @RequestBody DeptSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.create(body));
    }
    @PostMapping("/update")
    @PreAuthorize("@ss.hasPermission('system:dept:update')")
    public CommonResult<Long> update(@Valid @RequestBody DeptSaveReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.update(body));
    }
    @PostMapping("/update-status")
    @PreAuthorize("@ss.hasPermission('system:dept:update')")
    public CommonResult<Long> status(@Valid @RequestBody VersionedStatusReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.status(body));
    }
    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    public CommonResult<Long> delete(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.delete(body));
    }
    @PostMapping("/restore")
    @PreAuthorize("@ss.hasPermission('system:dept:restore')")
    public CommonResult<Long> restore(@Valid @RequestBody VersionedIdReqVO body,HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response); return CommonResult.success(service.restore(body));
    }
    private void prepare(HttpServletRequest request, HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
