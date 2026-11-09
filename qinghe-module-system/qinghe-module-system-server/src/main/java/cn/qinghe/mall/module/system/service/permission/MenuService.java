package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;

import java.util.List;

public interface MenuService {
    /** 授权配置回显包含停用资源，不能把未展示资源误认为用户主动取消。 */
    List<MenuDO> getConfiguredMenuList();

    /** 角色授权候选：当前端/套餐内且自身及祖先启用，调用方须先验证授权权限。 */
    List<MenuDO> getAssignableMenuList();

    /** 只过滤传入集合，不补祖先或扩大授权；不包含经营资格检查。 */
    List<MenuDO> filterDisableMenus(List<MenuDO> menus);
}
