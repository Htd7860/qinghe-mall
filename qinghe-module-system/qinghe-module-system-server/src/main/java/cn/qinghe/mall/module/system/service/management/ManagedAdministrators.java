package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.ManagementMutexDO;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.ManagementMutexMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 只按开通事务明确保存的引用识别管理员，不按角色码、姓名或租户0推断。 */
@Component
@RequiredArgsConstructor
public class ManagedAdministrators {
    private final TenantMapper tenantMapper;
    private final ManagementMutexMapper mutexMapper;
    public void requireOrdinaryUser(Long tenantId, Long userId) {
        Long designated;
        if (tenantId == 0) {
            ManagementMutexDO row = mutexMapper.selectById(1L);
            designated = row == null ? null : row.getPlatformAdminUserId();
        } else {
            TenantDO row = tenantMapper.selectById(tenantId);
            designated = row == null ? null : row.getContactUserId();
        }
        if (Objects.equals(designated, userId)) {
            throw exception0(403, "指定管理员不能在普通员工入口停用、删除或调整角色");
        }
    }
}
