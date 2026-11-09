package cn.qinghe.mall.module.system.service.management;

import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.dal.dataobject.permission.ManagementAuditDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.ManagementAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** details由领域Service明确构造；不接收外部任意对象或密码/Token快照。 */
@Component
@RequiredArgsConstructor
public class ManagementAudit {
    private final ManagementAuditMapper mapper;

    public void record(ManagementTransactions.Actor actor, Long targetTenantId, String action,
            Long targetId, Long fromVersion, Long toVersion, Map<String, Object> details) {
        MybatisUtils.requireWriteTransaction();
        if (targetTenantId == null || targetTenantId < 0 || targetId == null || targetId <= 0
                || fromVersion == null || fromVersion < -1 || fromVersion == Long.MAX_VALUE
                || toVersion == null || toVersion != fromVersion + 1
                || action == null || !action.matches("(ROLE|USER|TENANT|PACKAGE|MENU|DEPT)\\.[A-Z_]+")
                || details == null) {
            throw new IllegalArgumentException("管理审计参数不合法");
        }
        ManagementAuditDO row = new ManagementAuditDO();
        row.setTenantId(targetTenantId);
        row.setActorTenantId(actor.getTenantId());
        row.setActorUserId(actor.getId());
        row.setAction(action);
        row.setTargetId(targetId);
        row.setFromVersion(fromVersion);
        row.setToVersion(toVersion);
        row.setDetails(new LinkedHashMap<>(details));
        row.setTraceId(ManagementTransactions.traceId());
        row.setCreateTime(actor.getNow());
        // 只有领域Service可以指定治理目标；商家API不能提交任意tenantId。
        TenantUtils.execute(targetTenantId, () -> {
            if (mapper.insert(row) != 1) {
                throw new IllegalStateException("管理审计保存异常");
            }
            return null;
        });
    }
}
