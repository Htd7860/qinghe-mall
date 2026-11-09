package cn.qinghe.mall.module.system.dal.dataobject.permission;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 平台维护的全局资源目录；realm隔离平台/商家资源，忽略SQL租户条件不代表免权限。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_menu")
@TenantIgnore
public class MenuDO extends BaseDo {

    public static final Long ID_ROOT = 0L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String realm;
    private String name;
    private String permission;
    /** 与yudao一致：1目录、2菜单、3按钮/接口权限资源。 */
    private Integer type;
    private Integer sort;
    private Long parentId;
    private String path;
    private String icon;
    private String component;
    private String componentName;
    private Integer status;
    /** 仅控制导航展示，不能代替status或后端权限检查。 */
    private Boolean visible;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private Long authVersion;
}
