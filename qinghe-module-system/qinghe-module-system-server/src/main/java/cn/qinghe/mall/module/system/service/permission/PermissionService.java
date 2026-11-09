package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.module.system.service.permission.dto.UserPermissionDTO;

import java.util.Set;

public interface PermissionService {

    /**
     * 查询可信租户上下文内用户的当前角色/资源权限，多角色并集与套餐范围交集。
     * 内部调用方必须先完成账号认证/资格校验；本方法不把外部userId当作登录身份。
     * 不缓存、不授予超级管理员兜底；存储/配置异常继续抛出，不转成空权限成功。
     */
    UserPermissionDTO getUserPermission(Long userId);

    /** 本租户角色授权配置回显，包含套餐内停用资源的原绑定，不恢复或写入关联。 */
    Set<Long> getRoleMenuListByRoleId(Long roleId);
}
