package cn.qinghe.mall.module.system.service.permission;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.permission.PermissionAssignRoleDataScopeReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.dal.mysql.dept.DeptMapper;
import cn.qinghe.mall.module.system.service.management.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
@Service
@RequiredArgsConstructor
public class RoleDataScopeGrantService {
    private final ManagementTransactions transactions;
    private final RoleMapper roleMapper;
    private final DeptMapper deptMapper;
    private final ManagementAudit audit;
    public Long assign(PermissionAssignRoleDataScopeReqVO body) {
        Set<Long> deptIds = new TreeSet<>(body.getDeptIds());
        return transactions.execute("system:permission:assign-role-data-scope", actor -> {
            RoleDO role = roleMapper.selectByIdAndTenantForUpdate(body.getId());
            if (role == null) { throw exception0(404, "角色不存在"); }
            if (!Objects.equals(role.getAuthVersion(), body.getExpectedVersion())) { throw exception0(409, "角色已变化，请重新读取"); }
            if (Integer.valueOf(1).equals(role.getType())) { throw exception0(403, "内置角色数据范围由开通流程维护"); }
            if (!Integer.valueOf(2).equals(body.getDataScope()) && !deptIds.isEmpty()) {
                throw exception0(400, "只有指定部门模式可以提交部门集合");
            }
            Set<Long> valid = deptMapper.selectTenantTree().stream().filter(d -> Integer.valueOf(0).equals(d.getStatus()))
                    .map(DeptDO::getId).collect(Collectors.toSet());
            if (!valid.containsAll(deptIds)) { throw exception0(400, "指定部门不存在或已停用"); }
            if (Objects.equals(role.getDataScope(), body.getDataScope()) && Objects.equals(role.getDataScopeDeptIds(), deptIds)) {
                return role.getAuthVersion();
            }
            if (roleMapper.updateDataScopeSql(actor.getTenantId(), role.getId(), role.getAuthVersion(),
                    body.getDataScope(), deptIds, actor.getId().toString(), actor.getNow()) != 1) {
                throw exception0(409, "角色已变化，请重新读取");
            }
            Map<String,Object> details = new LinkedHashMap<>();
            details.put("dataScope", body.getDataScope()); details.put("deptIds", deptIds);
            audit.record(actor, actor.getTenantId(), "ROLE.DATA_SCOPE", role.getId(), role.getAuthVersion(), role.getAuthVersion()+1, details);
            return role.getAuthVersion()+1;
        });
    }
}
