package cn.qinghe.mall.framework.tenant.core.aop;

import java.lang.annotation.*;

/**
 * 临时忽略SQL租户过滤，不代表跳过身份认证或接口权限。
 *
 * <p>标在方法上：由TenantIgnoreAspect处理经过Spring代理的同步调用，
 * 正常返回或抛异常后恢复原ignore；不支持自调用、private方法或异步执行传播。
 * 标在DO实体类上：由TenantDatabaseInterceptor识别该表，enable仅接受true/false。
 * 不把Service/Controller类标记自动解释为整类忽略或HTTP匿名放行。</p>
 *
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

    /** 方法支持返回Boolean.TRUE的SpEL及@bean引用；false不覆盖外层已开启的忽略。 */
    String enable() default "true";
}
