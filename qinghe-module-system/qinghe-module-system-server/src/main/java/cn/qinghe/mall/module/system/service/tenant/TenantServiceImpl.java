package cn.qinghe.mall.module.system.service.tenant;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.OAUTH2_TENANT_INVALID;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.TENANT_OPERATION_DISABLED;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.TENANT_OPERATION_EXPIRED;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.tenant
 * @Project：qinghe-mall
 * @name：TenantServiceImpl
 * @Date：2026/10/24 14:39
 * @Filename：TenantServiceImpl
 */
@Service
public class TenantServiceImpl implements TenantService{

    private final TenantMapper tenantMapper;
    private final TenantPackageService tenantPackageService;

    public TenantServiceImpl(TenantMapper tenantMapper,
                             TenantPackageService tenantPackageService){
        this.tenantMapper=tenantMapper;
        this.tenantPackageService=tenantPackageService;
    }

    @Override
    public TenantDO getTenant(Long id) {
        if(id==null||id<=0){
            throw new IllegalArgumentException("商家租户编号必须为正数");
        }
        return tenantMapper.selectById(id);
    }

    @Override
    public TenantDO getTenantByCode(String code) {
        return tenantMapper.selectByCode(code);
    }

    @Override
    public TenantDO validateForAuthentication(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("商家租户身份数据异常");
        }

        TenantDO tenant = getTenant(tenantId);
        if (tenant == null || Boolean.TRUE.equals(tenant.getDeleted())) {
            throw exception(OAUTH2_TENANT_INVALID);
        }

        if (!tenantId.equals(tenant.getId())
                || (!CommonStatusEnum.isEnable(tenant.getStatus())
                    && !CommonStatusEnum.isDisable(tenant.getStatus()))
                || tenant.getPackageId() == null || tenant.getPackageId() <= 0
                || tenant.getExpireTime() == null
                || tenant.getAccountCount() == null || tenant.getAccountCount() < 1
                || tenant.getAuthVersion() == null || tenant.getAuthVersion() < 0) {
            throw new IllegalStateException("租户配置异常");
        }

        // 已有关联允许读取停用套餐，不能调用 validateForAssignment。
        TenantPackageDO tenantPackage =
                tenantPackageService.getTenantPackage(tenant.getPackageId());

        if (tenantPackage == null
                || Boolean.TRUE.equals(tenantPackage.getDeleted())
                || !tenant.getPackageId().equals(tenantPackage.getId())
                || (!CommonStatusEnum.isEnable(tenantPackage.getStatus())
                    && !CommonStatusEnum.isDisable(tenantPackage.getStatus()))
                || tenantPackage.getAuthVersion() == null
                || tenantPackage.getAuthVersion() < 0
                || tenantPackage.getMenuIds() == null
                || tenantPackage.getMenuIds().stream()
                    .anyMatch(menuId -> menuId == null || menuId <= 0)) {
            throw new IllegalStateException("租户关联套餐配置异常");
        }

        return tenant;
    }

    @Override
    public TenantDO validateForOperation(Long tenantId) {
        TenantDO tenant = validateForAuthentication(tenantId);

        if (CommonStatusEnum.isDisable(tenant.getStatus())) {
            throw exception(TENANT_OPERATION_DISABLED);
        }
        if (!tenant.getExpireTime().isAfter(LocalDateTime.now())) {
            throw exception(TENANT_OPERATION_EXPIRED);
        }

        return tenant;
    }
}
