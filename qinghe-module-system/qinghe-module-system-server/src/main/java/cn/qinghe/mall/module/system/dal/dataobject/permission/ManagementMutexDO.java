package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 只供内部管理事务锁定，不暴露通用HTTP修改接口。 */
@Data
@TenantIgnore
@TableName("system_management_mutex")
public class ManagementMutexDO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long platformAdminUserId;
    private Long platformAdminRoleId;
}
