package cn.qinghe.mall.module.system.controller.admin.permission;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RolePageReqVO;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RoleRespVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.service.permission.RoleService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;

/** 两端分别声明精确入口，直连system也必须核对端，不能只依赖Gateway。 */
@RestController
@Validated
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/admin-api/system/role/page")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<PageResult<RoleRespVO>> getMerchantRolePage(
            @Valid RolePageReqVO reqVO, HttpServletResponse response) {
        checkRealm(false);
        return rolePage(reqVO, response);
    }

    @GetMapping("/platform-api/system/role/page")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<PageResult<RoleRespVO>> getPlatformRolePage(
            @Valid RolePageReqVO reqVO, HttpServletResponse response) {
        checkRealm(true);
        return rolePage(reqVO, response);
    }

    @GetMapping("/admin-api/system/role/get")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<RoleRespVO> getMerchantRole(
            @RequestParam("id") @Min(1) Long id, HttpServletResponse response) {
        checkRealm(false);
        return roleDetail(id, response);
    }

    @GetMapping("/platform-api/system/role/get")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<RoleRespVO> getPlatformRole(
            @RequestParam("id") @Min(1) Long id, HttpServletResponse response) {
        checkRealm(true);
        return roleDetail(id, response);
    }

    private CommonResult<PageResult<RoleRespVO>> rolePage(
            RolePageReqVO reqVO, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        PageResult<RoleDO> page = roleService.getRolePage(reqVO);
        List<RoleRespVO> list = page.getList().stream()
                .map(this::toRespVO).collect(Collectors.toList());
        return CommonResult.success(new PageResult<>(page.getTotal(), list));
    }

    private CommonResult<RoleRespVO> roleDetail(Long id, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return CommonResult.success(toRespVO(roleService.getRole(id)));
    }

    private void checkRealm(boolean platform) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            throw exception(UNAUTHORIZED);
        }
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || user.getTenantId() == null
                || (platform ? user.getTenantId() != 0 : user.getTenantId() <= 0)) {
            throw exception(FORBIDDEN);
        }
    }

    private RoleRespVO toRespVO(RoleDO role) {
        RoleRespVO result = new RoleRespVO();
        result.setId(role.getId());
        result.setName(role.getName());
        result.setCode(role.getCode());
        result.setSort(role.getSort());
        result.setStatus(role.getStatus());
        result.setType(role.getType());
        result.setRemark(role.getRemark());
        result.setDataScope(role.getDataScope());
        result.setDataScopeDeptIds(new LinkedHashSet<>(role.getDataScopeDeptIds()));
        result.setAuthVersion(role.getAuthVersion());
        result.setDeleted(role.getDeleted());
        result.setCreateTime(role.getCreateTime());
        return result;
    }
}
