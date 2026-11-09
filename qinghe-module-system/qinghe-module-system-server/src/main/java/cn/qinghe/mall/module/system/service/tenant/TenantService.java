package cn.qinghe.mall.module.system.service.tenant;

import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.tenant
 * @Project：qinghe-mall
 * @name：TenantService
 * @Date：2026/10/24 14:38
 * @Filename：TenantService
 */
public interface TenantService {

    TenantDO getTenant(Long id);

    TenantDO getTenantByCode(String code);

    /** 检查令牌所属商家租户及关联配置，不在这里拒绝运营停用或到期。 */
    TenantDO validateForAuthentication(Long tenantId);

    /** 普通经营操作前检查启用和到期，不用于历史查询或受限售后履约。 */
    TenantDO validateForOperation(Long tenantId);
}
