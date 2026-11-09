package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/** 专用于成功的角色菜单变更；不继承带逻辑删除/更新字段的BaseDo。 */
@Data
@TenantIgnore(enable = "false")
@TableName(value = "system_role_menu_audit", autoResultMap = true)
public class RoleMenuAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long roleId;
    private Long actorUserId;
    private Long fromVersion;
    private Long toVersion;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> beforeMenuIds;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> afterMenuIds;
    /** 当前MDC未提供时为空，不伪造全链路追踪已接通。 */
    private String traceId;
    private LocalDateTime createTime;
}
