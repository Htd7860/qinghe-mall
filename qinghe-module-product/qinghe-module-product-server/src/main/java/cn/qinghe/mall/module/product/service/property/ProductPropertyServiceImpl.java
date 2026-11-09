package cn.qinghe.mall.module.product.service.property;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.controller.admin.property.vo.property.*;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyDO;
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

/** yudao属性项CRUD和重名检查；销售/展示属性类型固定、改名只同步当前商品快照。 @author 芋道源码 */
@Service
@Validated
public class ProductPropertyServiceImpl implements ProductPropertyService {
    private final ProductPropertyMapper mapper;
    private final ProductPropertyValueMapper values;
    private final ProductPropertyReferenceService references;
    private final ProductCatalogTransactions transactions;

    public ProductPropertyServiceImpl(ProductPropertyMapper mapper, ProductPropertyValueMapper values,
            ProductPropertyReferenceService references, ProductCatalogTransactions transactions) {
        this.mapper = mapper; this.values = values; this.references = references; this.transactions = transactions;
    }

    @Override
    public Long createProperty(ProductPropertySaveReqVO body) {
        return transactions.execute("product:property:create", actor -> {
            if (body.getId() != null) { throw exception0(400, "新属性不能指定编号"); }
            ProductPropertyDO same = mapper.selectByName(body.getName().trim(), body.getType());
            // 原版同名直接返回；本项目还须确认提交的状态/备注与已有值相同。
            if (same != null) {
                if (!Objects.equals(same.getStatus(), body.getStatus()) || !Objects.equals(same.getRemark(), remark(body.getRemark()))) {
                    throw exception(PROPERTY_EXISTS);
                }
                return same.getId();
            }
            ProductPropertyDO row = BeanUtils.toBean(body, ProductPropertyDO.class);
            row.setName(body.getName().trim()); row.setRemark(remark(body.getRemark())); row.setAuthVersion(0L);
            transactions.created(row, actor);
            if (mapper.insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("属性保存失败"); }
            transactions.audit(actor, "PROPERTY.CREATE", row.getId(), -1L, 0L, Collections.singletonMap("type", row.getType()));
            return row.getId();
        });
    }

    @Override
    public void updateProperty(ProductPropertySaveReqVO body) {
        transactions.execute("product:property:update", actor -> {
            ProductPropertyDO current = required(body.getId());
            transactions.requireVersion(current.getAuthVersion(), body.getExpectedVersion());
            if (!Objects.equals(current.getType(), body.getType())) { throw exception0(400, "属性类型创建后不可修改"); }
            ProductPropertyDO same = mapper.selectByName(body.getName().trim(), current.getType());
            if (same != null && !Objects.equals(same.getId(), current.getId())) { throw exception(PROPERTY_EXISTS); }
            ProductPropertyDO patch = BeanUtils.toBean(body, ProductPropertyDO.class);
            patch.setName(body.getName().trim()); patch.setRemark(remark(body.getRemark()));
            patch.setAuthVersion(current.getAuthVersion() + 1); transactions.updated(patch, actor);
            if (mapper.update(patch, new LambdaQueryWrapperX<ProductPropertyDO>().eq(ProductPropertyDO::getId, current.getId())
                    .eq(ProductPropertyDO::getAuthVersion, current.getAuthVersion())) != 1) {
                throw exception0(409, "属性已变化，请重新读取");
            }
            if (!Objects.equals(current.getName(), patch.getName())) { references.rename(current.getId(), patch.getName(), true, actor); }
            transactions.audit(actor, "PROPERTY.UPDATE", current.getId(), current.getAuthVersion(), patch.getAuthVersion(),
                    Collections.singletonMap("status", patch.getStatus()));
            return null;
        });
    }

    @Override
    public void deleteProperty(Long id, Long expectedVersion) {
        transactions.execute("product:property:delete", actor -> {
            ProductPropertyDO current = required(id);
            transactions.requireVersion(current.getAuthVersion(), expectedVersion);
            if (values.selectCountByPropertyId(id) > 0) { throw exception(PROPERTY_DELETE_FAIL_VALUE_EXISTS); }
            if (references.propertyReferenced(actor.getTenantId(), id)) { throw exception(PROPERTY_HAVE_BIND_SKU); }
            if (mapper.update(null, new LambdaUpdateWrapper<ProductPropertyDO>().eq(ProductPropertyDO::getId, id)
                    .eq(ProductPropertyDO::getAuthVersion, current.getAuthVersion())
                    .set(ProductPropertyDO::getDeleted, true).set(ProductPropertyDO::getAuthVersion, current.getAuthVersion() + 1)
                    .set(ProductPropertyDO::getUpdater, actor.getId().toString()).set(ProductPropertyDO::getUpdateTime, LocalDateTime.now())) != 1) {
                throw exception0(409, "属性已变化，请重新读取");
            }
            transactions.audit(actor, "PROPERTY.DELETE", id, current.getAuthVersion(), current.getAuthVersion() + 1, Collections.emptyMap());
            return null;
        });
    }

    @Override
    public PageResult<ProductPropertyDO> getPropertyPage(ProductPropertyPageReqVO body) { transactions.actor(); return mapper.selectPage(body); }
    @Override
    public ProductPropertyDO getProperty(Long id) { transactions.actor(); return required(id); }
    @Override
    public List<ProductPropertyDO> getPropertyList(Collection<Long> ids) {
        transactions.actor(); return ids == null || ids.isEmpty() ? Collections.emptyList() : mapper.selectByIds(ids);
    }
    @Override
    public List<ProductPropertyDO> getPropertyList() { transactions.actor(); return mapper.selectList(); }

    private ProductPropertyDO required(Long id) {
        ProductPropertyDO row = id == null ? null : mapper.selectById(id);
        if (row == null) { throw exception(PROPERTY_NOT_EXISTS); }
        return row;
    }
    private String remark(String text) { return text == null ? "" : text; }
}
