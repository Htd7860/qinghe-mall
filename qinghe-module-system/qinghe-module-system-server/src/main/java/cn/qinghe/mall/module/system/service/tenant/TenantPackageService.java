package cn.qinghe.mall.module.system.service.tenant;

import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;

public interface TenantPackageService {

    /** 读取套餐，不存在返回 null，停用套餐仍可读到。 */
    TenantPackageDO getTenantPackage(Long id);

    /** 新开通租户或切换套餐前，检查目标套餐是否允许分配。 */
    TenantPackageDO validateForAssignment(Long id);
}
