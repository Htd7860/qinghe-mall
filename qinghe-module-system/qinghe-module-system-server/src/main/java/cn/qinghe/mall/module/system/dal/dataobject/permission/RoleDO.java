package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/** 租户内角色；平台角色归属0，角色码逻辑删除后仍保留。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "system_role", autoResultMap = true)
public class RoleDO extends TenantBaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Integer sort;
    /** 0启用、1停用，停用不删除原有绑定。 */
    private Integer status;
    /** 与yudao一致：1内置、2自定义；不能仅凭code认定超级管理员。 */
    private Integer type;
    private String remark;
    /** 1本租户全部、2指定部门、3本部门、4部门及下级、5本人；员工管理已显式应用。 */
    private Integer dataScope;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> dataScopeDeptIds;
    /** 角色授权变更版本；后续写服务负责条件更新，不因字段存在自动防旧请求。 */
    private Long authVersion;
}
