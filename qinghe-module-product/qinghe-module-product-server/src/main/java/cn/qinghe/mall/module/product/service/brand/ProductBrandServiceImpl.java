package cn.qinghe.mall.module.product.service.brand;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.controller.admin.brand.vo.*;
import cn.qinghe.mall.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.qinghe.mall.module.product.dal.mysql.brand.ProductBrandMapper;
import cn.qinghe.mall.module.product.dal.mysql.catalog.ProductCatalogReferenceMapper;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import cn.qinghe.mall.module.product.framework.file.ProductFileService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;
import java.util.*;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.enums.ErrorCodeConstants.*;

/**
 * yudao品牌存在/重名校验、CRUD和启用校验；增加真实商品引用、可信租户及版本审计。
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductBrandServiceImpl implements ProductBrandService {
    private final ProductBrandMapper mapper;
    private final ProductCatalogReferenceMapper references;
    private final ProductCatalogTransactions transactions;
    private final ProductFileService files;

    public ProductBrandServiceImpl(ProductBrandMapper mapper, ProductCatalogReferenceMapper references,
                                   ProductCatalogTransactions transactions, ProductFileService files) {
        this.mapper = mapper; this.references = references; this.transactions = transactions;
        this.files = files;
    }

    @Override
    public Long createBrand(ProductBrandCreateReqVO body) {
        return transactions.execute("product:brand:create", actor -> {
            validateName(null, body.getName().trim());
            ProductBrandDO row = BeanUtils.toBean(body, ProductBrandDO.class);
            row.setName(body.getName().trim()); row.setDescription(body.getDescription() == null ? "" : body.getDescription());
            row.setAuthVersion(0L); transactions.created(row, actor);
            if (mapper.insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("品牌保存失败"); }
            files.bind("BRAND", row.getId(), row.getPicUrl());
            transactions.audit(actor, "BRAND.CREATE", row.getId(), -1L, 0L, Collections.singletonMap("status", row.getStatus()));
            return row.getId();
        });
    }

    @Override
    public void updateBrand(ProductBrandUpdateReqVO body) {
        transactions.execute("product:brand:update", actor -> {
            ProductBrandDO current = required(body.getId());
            transactions.requireVersion(current.getAuthVersion(), body.getExpectedVersion());
            validateName(current.getId(), body.getName().trim());
            ProductBrandDO patch = BeanUtils.toBean(body, ProductBrandDO.class);
            patch.setName(body.getName().trim()); patch.setDescription(body.getDescription() == null ? "" : body.getDescription());
            files.bind("BRAND", current.getId(), patch.getPicUrl());
            patch.setAuthVersion(current.getAuthVersion() + 1); transactions.updated(patch, actor);
            if (mapper.update(patch, new LambdaQueryWrapperX<ProductBrandDO>().eq(ProductBrandDO::getId, current.getId())
                    .eq(ProductBrandDO::getAuthVersion, current.getAuthVersion())) != 1) {
                throw exception0(409, "品牌已变化，请重新读取");
            }
            transactions.audit(actor, "BRAND.UPDATE", current.getId(), current.getAuthVersion(), patch.getAuthVersion(),
                    Collections.singletonMap("status", patch.getStatus()));
            return null;
        });
    }

    @Override
    public void deleteBrand(Long id, Long expectedVersion) {
        transactions.execute("product:brand:delete", actor -> {
            ProductBrandDO current = required(id);
            transactions.requireVersion(current.getAuthVersion(), expectedVersion);
            if (references.countBrand(actor.getTenantId(), id) > 0) { throw exception(BRAND_HAVE_BIND_SPU); }
            if (mapper.update(null, new LambdaUpdateWrapper<ProductBrandDO>().eq(ProductBrandDO::getId, id)
                    .eq(ProductBrandDO::getAuthVersion, current.getAuthVersion())
                    .set(ProductBrandDO::getDeleted, true).set(ProductBrandDO::getAuthVersion, current.getAuthVersion() + 1)
                    .set(ProductBrandDO::getUpdater, actor.getId().toString()).set(ProductBrandDO::getUpdateTime, LocalDateTime.now())) != 1) {
                throw exception0(409, "品牌已变化，请重新读取");
            }
            transactions.audit(actor, "BRAND.DELETE", id, current.getAuthVersion(), current.getAuthVersion() + 1, Collections.emptyMap());
            return null;
        });
    }

    @Override
    public ProductBrandDO getBrand(Long id) { transactions.actor(); return required(id); }

    @Override
    public List<ProductBrandDO> getBrandList(Collection<Long> ids) {
        transactions.actor();
        return ids == null || ids.isEmpty() ? Collections.emptyList() : mapper.selectByIds(ids);
    }

    @Override
    public List<ProductBrandDO> getBrandList(ProductBrandListReqVO body) { transactions.actor(); return mapper.selectList(body); }

    @Override
    public PageResult<ProductBrandDO> getBrandPage(ProductBrandPageReqVO body) { transactions.actor(); return mapper.selectPage(body); }

    @Override
    public List<ProductBrandDO> getBrandListByStatus(Integer status) { transactions.actor(); return mapper.selectListByStatus(status); }

    @Override
    public void validateProductBrand(Long id) {
        transactions.actor();
        if (!CommonStatusEnum.isEnable(required(id).getStatus())) { throw exception(BRAND_DISABLED); }
    }

    private void validateName(Long id, String name) {
        ProductBrandDO same = mapper.selectByName(name);
        if (same != null && !Objects.equals(same.getId(), id)) { throw exception(BRAND_NAME_EXISTS); }
    }

    private ProductBrandDO required(Long id) {
        ProductBrandDO row = id == null ? null : mapper.selectById(id);
        if (row == null) { throw exception(BRAND_NOT_EXISTS); }
        return row;
    }
}
