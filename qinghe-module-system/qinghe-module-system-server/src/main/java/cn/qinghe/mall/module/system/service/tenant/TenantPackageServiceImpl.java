package cn.qinghe.mall.module.system.service.tenant;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantPackageMapper;
import org.springframework.stereotype.Service;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

@Service
public class TenantPackageServiceImpl implements TenantPackageService {

    private final TenantPackageMapper packageMapper;

    public TenantPackageServiceImpl(TenantPackageMapper packageMapper) {
        this.packageMapper = packageMapper;
    }

    @Override
    public TenantPackageDO getTenantPackage(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("套餐编号必须为正数");
        }
        return packageMapper.selectById(id);
    }

    @Override
    public TenantPackageDO validateForAssignment(Long id) {
        TenantPackageDO tenantPackage = getTenantPackage(id);
        if (tenantPackage == null || Boolean.TRUE.equals(tenantPackage.getDeleted())) {
            throw exception0(NOT_FOUND.getCode(), "租户套餐不存在");
        }

        if (!id.equals(tenantPackage.getId())
                || (!CommonStatusEnum.isEnable(tenantPackage.getStatus())
                    && !CommonStatusEnum.isDisable(tenantPackage.getStatus()))
                || tenantPackage.getAuthVersion() == null
                || tenantPackage.getAuthVersion() < 0
                || tenantPackage.getMenuIds() == null
                || tenantPackage.getMenuIds().stream()
                    .anyMatch(menuId -> menuId == null || menuId <= 0)) {
            throw new IllegalStateException("租户套餐配置异常");
        }

        if (CommonStatusEnum.isDisable(tenantPackage.getStatus())) {
            throw exception0(FORBIDDEN.getCode(), "该套餐已停用，不能新开通或切入");
        }

        return tenantPackage;
    }
}
