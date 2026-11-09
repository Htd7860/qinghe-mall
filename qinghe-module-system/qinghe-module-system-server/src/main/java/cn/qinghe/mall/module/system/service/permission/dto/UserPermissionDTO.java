package cn.qinghe.mall.module.system.service.permission.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 当前有效角色显示名称与精确权限标识；不包含DO、凭据或数据范围执行结果。 */
public class UserPermissionDTO {

    private final List<String> roleNames;
    private final List<String> permissions;
    private final List<cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO> menus;

    public UserPermissionDTO(List<String> roleNames, List<String> permissions) {
        this(roleNames, permissions, Collections.emptyList());
    }

    public UserPermissionDTO(List<String> roleNames, List<String> permissions,
            List<cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO> menus) {
        this.roleNames = Collections.unmodifiableList(new ArrayList<>(roleNames));
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
        this.menus = Collections.unmodifiableList(new ArrayList<>(menus));
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public List<String> getPermissions() {
        return permissions;
    }
    public List<cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO> getMenus() { return menus; }
}
