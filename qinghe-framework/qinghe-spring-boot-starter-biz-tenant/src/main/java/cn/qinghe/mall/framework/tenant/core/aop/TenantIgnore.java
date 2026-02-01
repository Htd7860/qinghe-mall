package cn.qinghe.mall.framework.tenant.core.aop;

import java.lang.annotation.*;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.core.aop
 * @Project：qinghe-mall
 * @name：TenantIgnore
 * @Date：2026/10/21 17:18
 * @Filename：TenantIgnore
 */
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TenantIgnore {

    String enable() default "true";
}
