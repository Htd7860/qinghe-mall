package cn.qinghe.mall.framework.tenant.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.core.context
 * @Project：qinghe-mall
 * @name：TenantContextHolder
 * @Date：2026/10/21 16:49
 * @Filename：TenantContextHolder
 *
 * <p>用户编写上下文字段、getTenantId 和 clear；其余存取方法由助手按授权补齐。
 * 本类只保存执行上下文，不负责校验租户身份；调用方必须在执行结束后清理或恢复。</p>
 */
public class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_ID=new TransmittableThreadLocal<>();

    private static final ThreadLocal<Boolean> IGNORE=new TransmittableThreadLocal<>();

    /**
     *
     * @return 租户编号
     */
    public static Long getTenantId(){
        return TENANT_ID.get();
    }

    /** 必须存在租户上下文时使用，缺失则失败，不回退到默认租户。 */
    public static Long getRequiredTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("当前执行上下文缺少租户编号");
        }
        return tenantId;
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /** 仅供可信的后端流程设置；不能直接由客户端请求参数控制。 */
    public static void setIgnore(Boolean ignore) {
        IGNORE.set(ignore);
    }

    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE.get());
    }


    public static void clear(){
        TENANT_ID.remove();
        IGNORE.remove();
    }
}
