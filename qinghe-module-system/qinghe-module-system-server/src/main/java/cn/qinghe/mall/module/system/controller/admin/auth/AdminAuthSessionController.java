package cn.qinghe.mall.module.system.controller.admin.auth;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.auth.vo.AuthSessionRespVO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.service.permission.PermissionService;
import cn.qinghe.mall.module.system.service.permission.dto.UserPermissionDTO;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.user.AdminUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;

/** 仅查询当前已认证身份；不接受用户编号或可信租户编号，不公开匿名访问。 */
@RestController
public class AdminAuthSessionController {

    private final AdminUserService userService;
    private final TenantService tenantService;
    private final PermissionService permissionService;

    public AdminAuthSessionController(AdminUserService userService, TenantService tenantService,
                                      PermissionService permissionService) {
        this.userService = userService;
        this.tenantService = tenantService;
        this.permissionService = permissionService;
    }

    @GetMapping("/admin-api/auth/session")
    public CommonResult<AuthSessionRespVO> getMerchantSession(
            @RequestParam(value = "store", required = false) String store,
            HttpServletResponse response) {
        return currentIdentity(false, store, response);
    }

    @GetMapping("/platform-api/auth/session")
    public CommonResult<AuthSessionRespVO> getPlatformSession(
            @RequestParam(value = "store", required = false) String store,
            HttpServletResponse response) {
        return currentIdentity(true, store, response);
    }

    private CommonResult<AuthSessionRespVO> currentIdentity(
            boolean platform, String store, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw exception(UNAUTHORIZED);
        }
        if (!UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())
                || loginUser.getTenantId() == null
                || (platform ? loginUser.getTenantId() != 0 : loginUser.getTenantId() <= 0)) {
            throw exception(FORBIDDEN);
        }
        if (loginUser.getId() == null || loginUser.getId() <= 0
                || loginUser.getId() > 9_007_199_254_740_991L
                || loginUser.getExpiresTime() == null) {
            throw new IllegalStateException("当前身份无法生成浏览器投影");
        }
        if (!loginUser.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw exception(UNAUTHORIZED);
        }

        // 使用当前Token确认的ID重新读取资料，不能按store查询或切换到其他商家。
        AdminUserDO user = userService.validateUserForAuthentication(
                loginUser.getId(), loginUser.getTenantId());
        String tenantCode = "";
        if (!platform) {
            // 身份读取不属于经营写操作，保留停用/到期商家的历史履约身份边界。
            TenantDO tenant = tenantService.validateForAuthentication(loginUser.getTenantId());
            if (!StringUtils.hasText(tenant.getCode())) {
                throw new IllegalStateException("当前商家编码数据异常");
            }
            tenantCode = tenant.getCode();
        }
        if (store != null && !tenantCode.equals(store)) {
            throw exception(FORBIDDEN);
        }
        String name = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("当前账号显示信息异常");
        }
        UserPermissionDTO userPermission = permissionService.getUserPermission(loginUser.getId());

        AuthSessionRespVO result = new AuthSessionRespVO();
        result.setRealm(platform ? "platform" : "merchant");
        result.setUserId(loginUser.getId());
        result.setName(name);
        result.setTenantId(loginUser.getTenantId().toString());
        result.setTenantCode(tenantCode);
        // role仅作多角色名称展示，真正授权依据精确permissions和后端独立校验。
        result.setRole(String.join("、", userPermission.getRoleNames()));
        result.setPermissions(new ArrayList<>(userPermission.getPermissions()));
        result.setExpiresAt(loginUser.getExpiresTime().atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli());
        return CommonResult.success(result);
    }
}
