package cn.qinghe.mall.framework.security.core.service;

/** 供方法安全表达式使用；角色名称与OAuth scopes不替代资源权限。 */
public interface SecurityFrameworkService {

    boolean hasPermission(String permission);

    boolean hasAnyPermissions(String... permissions);
}
