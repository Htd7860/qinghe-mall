package cn.qinghe.mall.module.product.service.property;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.controller.admin.property.vo.value.*;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyDO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyValueDO;
import cn.qinghe.mall.module.product.dal.mysql.property.ProductPropertyMapper;
import cn.qinghe.mall.module.product.dal.mysql.property.ProductPropertyValueMapper;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;
import java.util.*;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.enums.ErrorCodeConstants.*;

/**
 * 取自yudao属性值服务；保留同名复用及改名同步，补真实父属性、启停/引用检查。
 *
 * @author LuoWenFeng
 */
@Service
@Validated
public class ProductPropertyValueServiceImpl implements ProductPropertyValueService {
    private final ProductPropertyValueMapper mapper;
    private final ProductPropertyMapper properties;
    private final ProductPropertyReferenceService references;
    private final ProductCatalogTransactions transactions;

    public ProductPropertyValueServiceImpl(ProductPropertyValueMapper mapper, ProductPropertyMapper properties,
            ProductPropertyReferenceService references, ProductCatalogTransactions transactions) {
        this.mapper = mapper; this.properties = properties; this.references = references; this.transactions = transactions;
    }

    @Override
    public Long createPropertyValue(ProductPropertyValueSaveReqVO body) {
        return transactions.execute("product:property:create", actor -> {
            if (body.getId() != null) { throw exception0(400, "新属性值不能指定编号"); }
            requireParent(body.getPropertyId(), body.getStatus());
            ProductPropertyValueDO same = mapper.selectByName(body.getPropertyId(), body.getName().trim());
            if (same != null) {
                if (!Objects.equals(same.getStatus(), body.getStatus()) || !Objects.equals(same.getRemark(), remark(body.getRemark()))) {
                    throw exception(PROPERTY_VALUE_EXISTS);
                }
                return same.getId();
            }
            ProductPropertyValueDO row = BeanUtils.toBean(body, ProductPropertyValueDO.class);
            row.setName(body.getName().trim()); row.setRemark(remark(body.getRemark())); row.setAuthVersion(0L);
            transactions.created(row, actor);
            if (mapper.insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("属性值保存失败"); }
            transactions.audit(actor, "VALUE.CREATE", row.getId(), -1L, 0L, Collections.singletonMap("propertyId", row.getPropertyId()));
            return row.getId();
        });
    }

    @Override
    public void updatePropertyValue(ProductPropertyValueSaveReqVO body) {
        transactions.execute("product:property:update", actor -> {
            ProductPropertyValueDO current = required(body.getId());
            transactions.requireVersion(current.getAuthVersion(), body.getExpectedVersion());
            if (!Objects.equals(current.getPropertyId(), body.getPropertyId())) {
                throw exception0(400, "属性值创建后不能转移到其他属性");
            }
            requireParent(current.getPropertyId(), body.getStatus());
            ProductPropertyValueDO same = mapper.selectByName(current.getPropertyId(), body.getName().trim());
            if (same != null && !Objects.equals(same.getId(), current.getId())) { throw exception(PROPERTY_VALUE_EXISTS); }
            ProductPropertyValueDO patch = BeanUtils.toBean(body, ProductPropertyValueDO.class);
            patch.setName(body.getName().trim()); patch.setRemark(remark(body.getRemark()));
            patch.setAuthVersion(current.getAuthVersion() + 1); transactions.updated(patch, actor);
            if (mapper.update(patch, new LambdaQueryWrapperX<ProductPropertyValueDO>()
                    .eq(ProductPropertyValueDO::getId, current.getId())
                    .eq(ProductPropertyValueDO::getAuthVersion, current.getAuthVersion())) != 1) {
                throw exception0(409, "属性值已变化，请重新读取");
            }
            if (!Objects.equals(current.getName(), patch.getName())) { references.rename(current.getId(), patch.getName(), false, actor); }
            transactions.audit(actor, "VALUE.UPDATE", current.getId(), current.getAuthVersion(), patch.getAuthVersion(),
                    Collections.singletonMap("status", patch.getStatus()));
            return null;
        });
    }

    @Override
    public void deletePropertyValue(Long id, Long expectedVersion) {
        transactions.execute("product:property:delete", actor -> {
            ProductPropertyValueDO current = required(id);
            transactions.requireVersion(current.getAuthVersion(), expectedVersion);
            if (references.valueReferenced(actor.getTenantId(), id)) { throw exception(PROPERTY_VALUE_HAVE_BIND_SKU); }
            if (mapper.update(null, new LambdaUpdateWrapper<ProductPropertyValueDO>().eq(ProductPropertyValueDO::getId, id)
                    .eq(ProductPropertyValueDO::getAuthVersion, current.getAuthVersion())
                    .set(ProductPropertyValueDO::getDeleted, true).set(ProductPropertyValueDO::getAuthVersion, current.getAuthVersion() + 1)
                    .set(ProductPropertyValueDO::getUpdater, actor.getId().toString()).set(ProductPropertyValueDO::getUpdateTime, LocalDateTime.now())) != 1) {
                throw exception0(409, "属性值已变化，请重新读取");
            }
            transactions.audit(actor, "VALUE.DELETE", id, current.getAuthVersion(), current.getAuthVersion() + 1, Collections.emptyMap());
            return null;
        });
    }

    @Override
    public ProductPropertyValueDO getPropertyValue(Long id) { transactions.actor(); return required(id); }
    @Override
    public List<ProductPropertyValueDO> getPropertyValueListByPropertyId(Collection<Long> ids) {
        transactions.actor(); return mapper.selectListByPropertyId(ids);
    }
    @Override
    public List<ProductPropertyValueDO> getPropertyValueListByIds(Collection<Long> ids) {
        transactions.actor();
        return ids == null || ids.isEmpty() ? Collections.emptyList() : mapper.selectByIds(ids);
    }

    @Override
    public Integer getPropertyValueCountByPropertyId(Long id) { transactions.actor(); return mapper.selectCountByPropertyId(id); }
    @Override
    public PageResult<ProductPropertyValueDO> getPropertyValuePage(ProductPropertyValuePageReqVO body) {
        transactions.actor(); return mapper.selectPage(body);
    }

    private ProductPropertyValueDO required(Long id) {
        ProductPropertyValueDO row = id == null ? null : mapper.selectById(id);
        if (row == null) { throw exception(PROPERTY_VALUE_NOT_EXISTS); }
        return row;
    }
    private void requireParent(Long id, Integer status) {
        ProductPropertyDO row = id == null ? null : properties.selectById(id);
        if (row == null) { throw exception(PROPERTY_NOT_EXISTS); }
        if (CommonStatusEnum.isEnable(status) && !CommonStatusEnum.isEnable(row.getStatus())) {
            throw exception0(400, "停用属性不能新增或启用属性值");
        }
    }
    private String remark(String text) { return text == null ? "" : text; }
}
