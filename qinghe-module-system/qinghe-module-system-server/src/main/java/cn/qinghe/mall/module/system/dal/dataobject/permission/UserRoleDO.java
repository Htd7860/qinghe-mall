package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 同租户用户与角色关联；撤销逻辑删除，显式重授恢复原行。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_user_role")
public class UserRoleDO extends TenantBaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;
}
