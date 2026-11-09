package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleMenuAuditDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Objects;
import java.util.stream.Stream;

@Mapper
public interface RoleMenuAuditMapper extends BaseMapper<RoleMenuAuditDO> {

    /** 必须由授权Service与绑定/版本变更在同一个可写事务内调用。 */
    default int insertForCurrentTenant(RoleMenuAuditDO audit) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("授权审计必须处于可信租户上下文");
        }
        Objects.requireNonNull(audit, "授权审计不能为空");
        if (!tenantId.equals(audit.getTenantId()) || audit.getId() != null
                || audit.getRoleId() == null || audit.getRoleId() <= 0
                || audit.getActorUserId() == null || audit.getActorUserId() <= 0
                || audit.getFromVersion() == null || audit.getFromVersion() < 0
                || audit.getFromVersion() == Long.MAX_VALUE || audit.getToVersion() == null
                || audit.getToVersion() != audit.getFromVersion() + 1
                || audit.getCreateTime() == null || audit.getTraceId() == null
                || audit.getTraceId().length() > 64
                || audit.getTraceId().chars().anyMatch(ch -> ch < 32 || ch > 126)) {
            throw new IllegalArgumentException("授权审计身份/版本/时间参数不合法");
        }
        if (audit.getBeforeMenuIds() == null || audit.getAfterMenuIds() == null
                || Stream.concat(audit.getBeforeMenuIds().stream(), audit.getAfterMenuIds().stream())
                        .anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("授权审计资源集合不合法");
        }
        return insert(audit);
    }
}
