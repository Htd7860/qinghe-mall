package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户角色与全局资源关联；所属端和套餐范围由后续授权服务核对。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_role_menu")
public class RoleMenuDO extends TenantBaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long menuId;
}
