package cn.qinghe.mall.module.system.controller.admin.permission;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedIdReqVO;
import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedStatusReqVO;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import cn.qinghe.mall.module.system.service.management.RoleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin-api/system/role", "/platform-api/system/role"})
public class RoleWriteController {
    private final RoleManagementService service;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('system:role:create')")
    public CommonResult<Long> create(@Valid @RequestBody RoleSaveReqVO body,
            HttpServletRequest request, HttpServletResponse response) {
        prepare(request, response);
        return CommonResult.success(service.create(body));
    }

    @PostMapping("/update")
    @PreAuthorize("@ss.hasPermission('system:role:update')")
    public CommonResult<Long> update(@Valid @RequestBody RoleSaveReqVO body,
            HttpServletRequest request, HttpServletResponse response) {
        prepare(request, response);
        return CommonResult.success(service.update(body));
    }

    @PostMapping("/update-status")
    @PreAuthorize("@ss.hasPermission('system:role:update')")
    public CommonResult<Long> status(@Valid @RequestBody VersionedStatusReqVO body,
            HttpServletRequest request, HttpServletResponse response) {
        prepare(request, response);
        return CommonResult.success(service.status(body));
    }

    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('system:role:delete')")
    public CommonResult<Long> delete(@Valid @RequestBody VersionedIdReqVO body,
            HttpServletRequest request, HttpServletResponse response) {
        prepare(request, response);
        return CommonResult.success(service.delete(body));
    }

    @PostMapping("/restore")
    @PreAuthorize("@ss.hasPermission('system:role:restore')")
    public CommonResult<Long> restore(@Valid @RequestBody VersionedIdReqVO body,
            HttpServletRequest request, HttpServletResponse response) {
        prepare(request, response);
        return CommonResult.success(service.restore(body));
    }

    private void prepare(HttpServletRequest request, HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
