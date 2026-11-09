package cn.qinghe.mall.module.product.service.property;

import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.mysql.catalog.ProductCatalogReferenceMapper;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/**
 * 从yudao ProductSkuServiceImpl.updateSkuProperty/updateSkuPropertyValue择取名称同步流程。
 * 分页处理，只更新properties与资料版本，不把整条旧SKU写回覆盖并发库存，也不改订单快照。
 *
 * @author 芋道源码
 */
@Service
public class ProductPropertyReferenceService {
    private final ProductCatalogReferenceMapper references;
    private final ProductSkuMapper skus;
    private final ProductSpuMapper spus;
    private final ProductCatalogTransactions transactions;

    public ProductPropertyReferenceService(ProductCatalogReferenceMapper references, ProductSkuMapper skus,
            ProductSpuMapper spus, ProductCatalogTransactions transactions) {
        this.references = references; this.skus = skus; this.spus = spus; this.transactions = transactions;
    }

    public boolean propertyReferenced(Long tenantId, Long id) {
        return references.countSalesProperty(tenantId, id) > 0 || references.countDisplayProperty(tenantId, id) > 0;
    }

    public boolean valueReferenced(Long tenantId, Long id) {
        return references.countSalesValue(tenantId, id) > 0 || references.countDisplayValue(tenantId, id) > 0;
    }

    public void rename(Long id, String name, boolean property, LoginUser actor) {
        MybatisUtils.requireWriteTransaction();
        String field = property ? "propertyId" : "valueId"; // 两个后端固定常量，不接受外部SQL标识。
        long cursor = 0;
        while (true) {
            List<ProductSkuDO> rows = skus.selectList(new LambdaQueryWrapperX<ProductSkuDO>()
                    .gt(ProductSkuDO::getId, cursor)
                    .apply("JSON_CONTAINS(properties,JSON_OBJECT('" + field + "',{0}))", id)
                    .orderByAsc(ProductSkuDO::getId).last("LIMIT 500"));
            if (rows.isEmpty()) { break; }
            for (ProductSkuDO row : rows) {
                renameItems(row.getProperties(), id, name, property);
                transactions.requireVersion(row.getAuthVersion(), row.getAuthVersion());
                ProductSkuDO patch = new ProductSkuDO();
                patch.setProperties(row.getProperties());
                patch.setAuthVersion(row.getAuthVersion() + 1);
                transactions.updated(patch, actor);
                if (skus.update(patch, new LambdaQueryWrapperX<ProductSkuDO>()
                        .eq(ProductSkuDO::getId, row.getId()).eq(ProductSkuDO::getAuthVersion, row.getAuthVersion())) != 1) {
                    throw new IllegalStateException("SKU规格名称同步冲突");
                }
            }
            cursor = rows.get(rows.size() - 1).getId();
        }
        cursor = 0;
        while (true) {
            List<ProductSpuDO> rows = spus.selectList(new LambdaQueryWrapperX<ProductSpuDO>()
                    .gt(ProductSpuDO::getId, cursor)
                    .apply("JSON_CONTAINS(display_properties,JSON_OBJECT('" + field + "',{0}))", id)
                    .orderByAsc(ProductSpuDO::getId).last("LIMIT 500"));
            if (rows.isEmpty()) { break; }
            for (ProductSpuDO row : rows) {
                renameItems(row.getDisplayProperties(), id, name, property);
                transactions.requireVersion(row.getAuthVersion(), row.getAuthVersion());
                ProductSpuDO patch = new ProductSpuDO();
                patch.setDisplayProperties(row.getDisplayProperties());
                patch.setAuthVersion(row.getAuthVersion() + 1);
                transactions.updated(patch, actor);
                if (spus.update(patch, new LambdaQueryWrapperX<ProductSpuDO>()
                        .eq(ProductSpuDO::getId, row.getId()).eq(ProductSpuDO::getAuthVersion, row.getAuthVersion())) != 1) {
                    throw new IllegalStateException("SPU展示属性名称同步冲突");
                }
            }
            cursor = rows.get(rows.size() - 1).getId();
        }
    }

    private void renameItems(List<ProductSkuDO.Property> items, Long id, String name, boolean property) {
        if (items == null) { throw new IllegalStateException("商品属性数据不完整"); }
        for (ProductSkuDO.Property item : items) {
            if (property && Objects.equals(item.getPropertyId(), id)) { item.setPropertyName(name); }
            if (!property && Objects.equals(item.getValueId(), id)) { item.setValueName(name); }
        }
    }
}
