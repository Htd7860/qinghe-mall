package cn.qinghe.mall.module.system.service.user;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.dept.DeptMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 员工读写明确应用同一数据范围；权限码负责能否操作，范围负责可操作哪些人员。 */
@Service
@RequiredArgsConstructor
public class UserDataScope {
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final DeptMapper deptMapper;
    private final AdminUserMapper userMapper;

    public Scope current() {
        LoginUser login = SecurityFrameworkUtils.getLoginUser();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (login == null || !tenantId.equals(login.getTenantId()) || TenantContextHolder.isIgnore()) {
            throw exception0(403, "人员数据范围身份异常");
        }
        AdminUserDO user = userMapper.selectByIdAndTenant(login.getId(), tenantId);
        if (user == null) { throw exception0(401, "账号已失效"); }
        Scope scope = new Scope(login.getId(), user.getDeptId());
        Set<Long> roleIds = userRoleMapper.selectListByUserId(login.getId()).stream()
                .map(UserRoleDO::getRoleId).collect(Collectors.toSet());
        Map<Long, DeptDO> depts = deptMapper.selectTenantTree().stream()
                .collect(Collectors.toMap(DeptDO::getId, d -> d));
        for (RoleDO role : roleMapper.selectListByIds(roleIds)) {
            if (!Integer.valueOf(0).equals(role.getStatus())) { continue; }
            Integer type = role.getDataScope();
            if (Integer.valueOf(1).equals(type)) { scope.all = true; }
            else if (Integer.valueOf(2).equals(type) && role.getDataScopeDeptIds() != null) {
                for (Long id : role.getDataScopeDeptIds()) {
                    if (depts.containsKey(id)) { scope.deptIds.add(id); }
                }
            } else if (Integer.valueOf(3).equals(type) && depts.containsKey(user.getDeptId())) {
                scope.deptIds.add(user.getDeptId());
            } else if (Integer.valueOf(4).equals(type) && depts.containsKey(user.getDeptId())) {
                for (Long id : depts.keySet()) {
                    Long cursor = id;
                    Set<Long> visited = new HashSet<>();
                    while (cursor != null && cursor > 0 && visited.add(cursor)) {
                        if (cursor.equals(user.getDeptId())) { scope.deptIds.add(id); break; }
                        DeptDO parent = depts.get(cursor);
                        cursor = parent == null ? null : parent.getParentId();
                    }
                }
            } else if (Integer.valueOf(5).equals(type)) { scope.self = true; }
        }
        return scope;
    }

    @Getter
    public static final class Scope {
        private boolean all;
        private boolean self;
        private final Long userId;
        private final Long currentDeptId;
        private final Set<Long> deptIds = new LinkedHashSet<>();
        private Scope(Long userId, Long currentDeptId) { this.userId = userId; this.currentDeptId = currentDeptId; }
        public boolean includes(AdminUserDO user) {
            return user != null && (all || (self && userId.equals(user.getId()))
                    || deptIds.contains(user.getDeptId()));
        }
        public void requireVisible(AdminUserDO user) {
            if (!includes(user)) { throw exception0(404, "人员不存在或不在可管理范围内"); }
        }
        public void requireDepartment(Long deptId) {
            if (!all && (deptId == null || !deptIds.contains(deptId))) {
                throw exception0(403, "目标部门不在可管理范围内");
            }
        }
    }
}
