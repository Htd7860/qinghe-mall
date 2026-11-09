package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TenantIgnore(enable = "false")
@TableName(value = "system_management_audit", autoResultMap = true)
public class ManagementAuditDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long actorTenantId;
    private Long actorUserId;
    private String action;
    private Long targetId;
    private Long fromVersion;
    private Long toVersion;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> details;
    private String traceId;
    private LocalDateTime createTime;
}
