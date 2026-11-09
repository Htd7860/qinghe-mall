package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RolePageReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import org.springframework.stereotype.Service;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.ROLE_NOT_EXISTS;

/** 角色管理属于普通经营入口；认证时权限聚合仍使用独立的PermissionService。 */
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final TenantService tenantService;

    public RoleServiceImpl(RoleMapper roleMapper, TenantService tenantService) {
        this.roleMapper = roleMapper;
        this.tenantService = tenantService;
    }

    @Override
    public RoleDO getRole(Long id) {
        validateManagementTenant();
        RoleDO role = roleMapper.selectByIdAndTenant(id);
        if (role == null) {
            // 不存在、已删除和其他租户的编号使用相同对外结果。
            throw exception(ROLE_NOT_EXISTS);
        }
        return role;
    }

    @Override
    public PageResult<RoleDO> getRolePage(RolePageReqVO reqVO) {
        validateManagementTenant();
        return roleMapper.selectPage(reqVO);
    }

    private void validateManagementTenant() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色管理必须处于可信租户上下文");
        }
        if (tenantId > 0) {
            tenantService.validateForOperation(tenantId);
        }
    }
}
