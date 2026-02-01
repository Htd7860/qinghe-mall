package cn.qinghe.mall.framework.tenant.core.db;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.core.db
 * @Project：qinghe-mall
 * @name：tenantBaseDo
 * @Date：2026/10/21 16:27
 * @Filename：tenantBaseDo
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantBaseDo extends BaseDo {
    private Long tenantId;
}
