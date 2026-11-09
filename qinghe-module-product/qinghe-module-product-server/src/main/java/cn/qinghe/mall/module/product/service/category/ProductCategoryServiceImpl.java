package cn.qinghe.mall.module.product.service.category;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.qinghe.mall.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.qinghe.mall.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.qinghe.mall.module.product.dal.mysql.catalog.ProductCatalogReferenceMapper;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import cn.qinghe.mall.module.product.framework.file.ProductFileService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.enums.ErrorCodeConstants.*;

/**
 * 取自yudao ProductCategoryServiceImpl；保留父级/子级/SPU引用检查流程。
 * 差异：最多三级且只有叶子可挂商品，树更新检查完整父链、循环与后代深度。
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductCategoryServiceImpl implements ProductCategoryService {
    private final ProductCategoryMapper mapper;
    private final ProductCatalogReferenceMapper references;
    private final ProductCatalogTransactions transactions;
    private final ProductFileService files;

    public ProductCategoryServiceImpl(ProductCategoryMapper mapper, ProductCatalogReferenceMapper references,
                                      ProductCatalogTransactions transactions, ProductFileService files) {
        this.mapper = mapper; this.references = references; this.transactions = transactions;
        this.files = files;
    }

    @Override
    public Long createCategory(ProductCategorySaveReqVO body) {
        return transactions.execute("product:category:create", actor -> {
            if (body.getId() != null) { throw exception0(400, "新分类不能指定编号"); }
            ProductCategoryDO row = BeanUtils.toBean(body, ProductCategoryDO.class);
            row.setName(body.getName().trim());
            row.setSort(body.getSort() == null ? 0 : body.getSort());
            row.setDescription(body.getDescription() == null ? "" : body.getDescription());
            Map<Long, ProductCategoryDO> tree = tree();
            validateName(tree.values(), null, row.getParentId(), row.getName());
            requireParentUnused(actor.getTenantId(), row.getParentId());
            tree.put(-1L, row);
            validateTree(tree);
            row.setAuthVersion(0L); transactions.created(row, actor);
            if (mapper.insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("分类保存失败"); }
            files.bind("CATEGORY", row.getId(), row.getPicUrl());
            transactions.audit(actor, "CATEGORY.CREATE", row.getId(), -1L, 0L,
                    Collections.singletonMap("parentId", row.getParentId()));
            return row.getId();
        });
    }

    @Override
    public void updateCategory(ProductCategorySaveReqVO body) {
        transactions.execute("product:category:update", actor -> {
            ProductCategoryDO current = required(body.getId());
            transactions.requireVersion(current.getAuthVersion(), body.getExpectedVersion());
            ProductCategoryDO patch = BeanUtils.toBean(body, ProductCategoryDO.class);
            patch.setName(body.getName().trim());
            patch.setSort(body.getSort() == null ? 0 : body.getSort());
            patch.setDescription(body.getDescription() == null ? "" : body.getDescription());
            Map<Long, ProductCategoryDO> tree = tree();
            validateName(tree.values(), current.getId(), patch.getParentId(), patch.getName());
            if (!Objects.equals(current.getParentId(), patch.getParentId())) {
                requireParentUnused(actor.getTenantId(), patch.getParentId());
            }
            tree.put(current.getId(), patch); validateTree(tree);
            files.bind("CATEGORY", current.getId(), patch.getPicUrl());
            patch.setAuthVersion(current.getAuthVersion() + 1); transactions.updated(patch, actor);
            if (mapper.update(patch, new LambdaQueryWrapperX<ProductCategoryDO>()
                    .eq(ProductCategoryDO::getId, current.getId())
                    .eq(ProductCategoryDO::getAuthVersion, current.getAuthVersion())) != 1) {
                throw exception0(409, "分类已变化，请重新读取");
            }
            transactions.audit(actor, "CATEGORY.UPDATE", current.getId(), current.getAuthVersion(), patch.getAuthVersion(),
                    Collections.singletonMap("parentId", patch.getParentId()));
            return null;
        });
    }

    @Override
    public void deleteCategory(Long id, Long expectedVersion) {
        transactions.execute("product:category:delete", actor -> {
            ProductCategoryDO current = required(id);
            transactions.requireVersion(current.getAuthVersion(), expectedVersion);
            if (mapper.selectCountByParentId(id) > 0) { throw exception(CATEGORY_EXISTS_CHILDREN); }
            if (references.countCategory(actor.getTenantId(), id) > 0) { throw exception(CATEGORY_HAVE_BIND_SPU); }
            if (mapper.update(null, new LambdaUpdateWrapper<ProductCategoryDO>().eq(ProductCategoryDO::getId, id)
                    .eq(ProductCategoryDO::getAuthVersion, current.getAuthVersion())
                    .set(ProductCategoryDO::getDeleted, true).set(ProductCategoryDO::getAuthVersion, current.getAuthVersion() + 1)
                    .set(ProductCategoryDO::getUpdater, actor.getId().toString()).set(ProductCategoryDO::getUpdateTime, LocalDateTime.now())) != 1) {
                throw exception0(409, "分类已变化，请重新读取");
            }
            transactions.audit(actor, "CATEGORY.DELETE", id, current.getAuthVersion(), current.getAuthVersion() + 1,
                    Collections.emptyMap());
            return null;
        });
    }

    @Override
    public ProductCategoryDO getCategory(Long id) { transactions.actor(); return required(id); }

    @Override
    public List<ProductCategoryDO> getCategoryList(ProductCategoryListReqVO body) {
        transactions.actor(); return mapper.selectList(body);
    }

    @Override
    public List<ProductCategoryDO> getEnableCategoryList() {
        transactions.actor();
        Map<Long, ProductCategoryDO> tree = tree();
        return tree.values().stream().filter(row -> enabledChain(row, tree))
                .sorted(Comparator.comparing(ProductCategoryDO::getSort).thenComparing(ProductCategoryDO::getId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductCategoryDO> getEnableCategoryList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) { return Collections.emptyList(); }
        Set<Long> selected = new HashSet<>(ids);
        return getEnableCategoryList().stream().filter(row -> selected.contains(row.getId())).collect(Collectors.toList());
    }

    @Override
    public void validateCategory(Long id) {
        transactions.actor();
        ProductCategoryDO row = required(id);
        if (!enabledChain(row, tree())) { throw exception(CATEGORY_DISABLED, row.getName()); }
        if (mapper.selectCountByParentId(id) > 0) { throw exception(SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR); }
    }

    @Override
    public void validateCategoryList(Collection<Long> ids) {
        if (ids != null) { for (Long id : new HashSet<>(ids)) { validateCategory(id); } }
    }

    @Override
    public Integer getCategoryLevel(Long id) {
        transactions.actor();
        if (Long.valueOf(0).equals(id)) { return 0; }
        return depth(id, tree());
    }

    private ProductCategoryDO required(Long id) {
        ProductCategoryDO row = id == null ? null : mapper.selectById(id);
        if (row == null) { throw exception(CATEGORY_NOT_EXISTS); }
        return row;
    }

    private Map<Long, ProductCategoryDO> tree() {
        return mapper.selectList().stream().collect(Collectors.toMap(ProductCategoryDO::getId, row -> row));
    }

    private void validateName(Collection<ProductCategoryDO> rows, Long id, Long parent, String name) {
        for (ProductCategoryDO row : rows) {
            if (!Objects.equals(row.getId(), id) && Objects.equals(row.getParentId(), parent)
                    && row.getName().equalsIgnoreCase(name)) { throw exception0(409, "同级分类名称已存在"); }
        }
    }

    private void requireParentUnused(Long tenant, Long parent) {
        if (parent != null && parent > 0 && references.countCategory(tenant, parent) > 0) {
            throw exception0(409, "父分类已挂商品，请先迁移商品");
        }
    }

    private void validateTree(Map<Long, ProductCategoryDO> tree) {
        for (Map.Entry<Long, ProductCategoryDO> entry : tree.entrySet()) {
            if (depth(entry.getKey(), tree) > ProductCategoryDO.CATEGORY_LEVEL) { throw exception(CATEGORY_DEPTH_EXCEEDED); }
            if (CommonStatusEnum.isEnable(entry.getValue().getStatus()) && !enabledChain(entry.getValue(), tree)) {
                throw exception0(400, "启用分类的所有父分类必须启用");
            }
        }
    }

    private int depth(Long id, Map<Long, ProductCategoryDO> tree) {
        Set<Long> seen = new HashSet<>(); int level = 0;
        while (!Long.valueOf(0).equals(id)) {
            if (id == null || !seen.add(id)) { throw exception(CATEGORY_TREE_CYCLE); }
            ProductCategoryDO row = tree.get(id);
            if (row == null) { throw exception(CATEGORY_PARENT_NOT_EXISTS); }
            level++; id = row.getParentId();
        }
        return level;
    }

    private boolean enabledChain(ProductCategoryDO row, Map<Long, ProductCategoryDO> tree) {
        Set<Long> seen = new HashSet<>();
        while (row != null) {
            // 新建模拟节点尚无id，使用-1作为校验身份。
            Long key = row.getId() == null ? -1L : row.getId();
            if (!seen.add(key) || !CommonStatusEnum.isEnable(row.getStatus())) { return false; }
            if (Long.valueOf(0).equals(row.getParentId())) { return true; }
            row = tree.get(row.getParentId());
        }
        return false;
    }
}
