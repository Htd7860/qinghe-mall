package cn.qinghe.mall.framework.tenant.core.util;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.core.util
 * @Project：qinghe-mall
 * @name：TenantUtils
 * @Date：2026/10/24 0:25
 * @Filename：TenantUtils
 */
public final class TenantUtils {

    private TenantUtils() {
    }

    /**
     * 在已由后端确认的租户内同步执行，结束后恢复调用前的上下文。
     * 仅切换执行上下文，不代替身份授权或实体归属赋值。
     */
    public static <T> T execute(Long tenantId, Supplier<T> action) {
        if (tenantId == null || tenantId < 0) {
            throw new IllegalArgumentException("目标租户编号不合法");
        }
        Objects.requireNonNull(action, "执行逻辑不能为空");

        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(false);
            return action.get();
        } finally {
            TenantContextHolder.setTenantId(oldTenantId);
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }

    /**
     * 临时忽略租户过滤，执行完后恢复原来的状态。
     * 仅用于可信后端的同步逻辑。
     */
    public static <T> T executeIgnore(Supplier<T> action) {
        Objects.requireNonNull(action, "执行逻辑不能为空");

        boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return action.get();
        } finally {
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }
}
