package cn.qinghe.mall.module.product.service.sku;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.framework.file.ProductFileService;
import cn.qinghe.mall.module.product.service.stock.ProductStockService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.qinghe.mall.module.product.convert.sku.ProductSkuConvert;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyDO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyValueDO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.enums.property.ProductPropertyTypeEnum;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import cn.qinghe.mall.module.product.service.property.ProductPropertyService;
import cn.qinghe.mall.module.product.service.property.ProductPropertyValueService;
import org.springframework.stereotype.Service;
import javax.validation.Validator;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.enums.ErrorCodeConstants.*;

/**
 * 择取yudao ProductSkuServiceImpl.validateSkuList和查询方法。
 * 保留单规格默认值/批量属性读取/逐SKU维度检查/组合去重，增加类型、属性值归属、状态与更新输入约束。
 * 原版差集新增/修改/删除改为稳定编号与停用，库存不被资料覆盖，新增量经库存流水入账。
 * @author 芋道源码
 */
@Service
public class ProductSkuServiceImpl implements ProductSkuService {
    private final ProductSkuMapper mapper;
    private final ProductPropertyService properties;
    private final ProductPropertyValueService values;
    private final ProductCatalogTransactions transactions;
    private final ProductSkuProperties settings;
    private final Validator validator;
    private final ProductSpuMapper spus;
    private final ProductStockService stocks;
    private final ProductFileService files;
    private final RemoteSystemSecurityClient remote;
    private final SecurityFrameworkService security;

    public ProductSkuServiceImpl(ProductSkuMapper mapper, ProductPropertyService properties,
            ProductPropertyValueService values, ProductCatalogTransactions transactions,
            ProductSkuProperties settings, Validator validator,ProductSpuMapper spus,ProductStockService stocks,
            ProductFileService files,RemoteSystemSecurityClient remote,SecurityFrameworkService security) {
        this.mapper = mapper; this.properties = properties; this.values = values;
        this.transactions = transactions; this.settings = settings; this.validator = validator;
        this.spus=spus;this.stocks=stocks;this.files=files;this.remote=remote;this.security=security;
    }

    @Override
    public void validateSkuList(List<ProductSkuSaveReqVO> skus, Boolean specType) {
        transactions.actor();
        if (skus == null || skus.isEmpty()) { throw exception(SKU_NOT_EXISTS); }
        if (skus.size() > settings.getMaxSkus()) { throw exception0(400, "商品SKU数量超过配置上限"); }
        if (specType == null) { throw exception0(400, "请选择单规格或多规格"); }
        if (!specType && skus.size() != 1) { throw exception0(400, "单规格商品必须且只能有一条SKU"); }
        Set<Long> ids = new HashSet<>();
        for (ProductSkuSaveReqVO sku : skus) {
            if (sku == null || !validator.validate(sku).isEmpty()) { throw exception0(400, "SKU字段不符合金额、图片或计量要求"); }
            if (sku.getId() == null) {
                if (sku.getExpectedVersion() != null || sku.getStock() == null) {
                    throw exception0(400, "新SKU须提供初始库存，不能指定资料版本");
                }
            } else if (!ids.add(sku.getId()) || sku.getExpectedVersion() == null || sku.getStock() != null) {
                throw exception0(400, "已有SKU须使用唯一编号和当前版本；库存请通过库存调整修改");
            }
        }
        if (!specType) {
            ProductSkuSaveReqVO sku = skus.get(0);
            List<ProductSkuSaveReqVO.Property> supplied = sku.getProperties();
            if (supplied != null && !supplied.isEmpty()
                    && !(supplied.size() == 1 && Long.valueOf(0).equals(supplied.get(0).getPropertyId())
                    && Long.valueOf(0).equals(supplied.get(0).getValueId()))) {
                throw exception0(400, "单规格商品不能携带销售规格组合");
            }
            sku.setProperties(Collections.singletonList(new ProductSkuSaveReqVO.Property(
                    ProductPropertyDO.ID_DEFAULT, ProductPropertyDO.NAME_DEFAULT,
                    ProductPropertyValueDO.ID_DEFAULT, ProductPropertyValueDO.NAME_DEFAULT)));
            return;
        }

        // 数量相同并不代表维度相同，必须比较真正的propertyId集合。
        Set<Long> dimensions = null;
        List<ProductSkuSaveReqVO.Property> selected = new ArrayList<>();
        for (ProductSkuSaveReqVO sku : skus) {
            if (sku.getProperties() == null || sku.getProperties().isEmpty()) { throw exception(SKU_PROPERTIES_EMPTY); }
            Set<Long> current = new HashSet<>();
            for (ProductSkuSaveReqVO.Property property : sku.getProperties()) {
                positivePair(property);
                if (!current.add(property.getPropertyId())) { throw exception(SKU_PROPERTIES_DUPLICATED); }
                selected.add(property);
            }
            if (dimensions != null && !dimensions.equals(current)) { throw exception(SPU_ATTR_NUMBERS_MUST_BE_EQUALS); }
            dimensions = current;
        }
        Map<Long, ProductPropertyDO> propertyMap = loadProperties(selected, ProductPropertyTypeEnum.SALES);
        Map<Long, ProductPropertyValueDO> valueMap = loadValues(selected);
        Set<String> combinations = new HashSet<>();
        List<List<ProductSkuSaveReqVO.Property>> normalized = new ArrayList<>();
        for (ProductSkuSaveReqVO sku : skus) {
            List<ProductSkuSaveReqVO.Property> snapshot = normalize(sku.getProperties(), propertyMap, valueMap,
                    CommonStatusEnum.isEnable(sku.getStatus()));
            ProductSkuDO keySource = new ProductSkuDO();
            keySource.setProperties(toProperties(snapshot));
            if (!combinations.add(ProductSkuConvert.buildPropertyKey(keySource))) { throw exception(SPU_SKU_NOT_DUPLICATE); }
            normalized.add(snapshot);
        }
        // 全部通过后再替换请求内的冗余名称；不信任客户端的propertyName/valueName。
        for (int i = 0; i < skus.size(); i++) { skus.get(i).setProperties(normalized.get(i)); }
    }

    @Override
    public List<ProductSkuDO.Property> validateDisplayProperties(List<ProductSkuSaveReqVO.Property> items) {
        transactions.actor();
        if (items == null || items.isEmpty()) { return Collections.emptyList(); }
        if (items.size() > 100) { throw exception0(400, "展示属性数量超过上限"); }
        Set<Long> seen = new HashSet<>();
        for (ProductSkuSaveReqVO.Property item : items) {
            positivePair(item);
            if (!seen.add(item.getPropertyId())) { throw exception0(400, "同一展示属性只能选择一个值"); }
        }
        return toProperties(normalize(items, loadProperties(items, ProductPropertyTypeEnum.DISPLAY), loadValues(items), true));
    }

    private void positivePair(ProductSkuSaveReqVO.Property item) {
        if (item == null || item.getPropertyId() == null || item.getPropertyId() <= 0
                || item.getValueId() == null || item.getValueId() <= 0) {
            throw exception0(400, "请选择真实属性项和属性值");
        }
    }

    private Map<Long, ProductPropertyDO> loadProperties(List<ProductSkuSaveReqVO.Property> selected, ProductPropertyTypeEnum type) {
        Set<Long> ids = selected.stream().map(ProductSkuSaveReqVO.Property::getPropertyId).collect(Collectors.toSet());
        Map<Long, ProductPropertyDO> result = properties.getPropertyList(ids).stream()
                .collect(Collectors.toMap(ProductPropertyDO::getId, row -> row));
        if (result.size() != ids.size()) { throw exception(PROPERTY_NOT_EXISTS); }
        for (ProductPropertyDO row : result.values()) {
            if (!Integer.valueOf(type.getValue()).equals(row.getType())) {
                throw exception0(400, type == ProductPropertyTypeEnum.SALES ? "SKU只能选择销售规格" : "展示属性不能选择销售规格");
            }
        }
        return result;
    }

    private Map<Long, ProductPropertyValueDO> loadValues(List<ProductSkuSaveReqVO.Property> selected) {
        Set<Long> ids = selected.stream().map(ProductSkuSaveReqVO.Property::getValueId).collect(Collectors.toSet());
        Map<Long, ProductPropertyValueDO> result = values.getPropertyValueListByIds(ids).stream()
                .collect(Collectors.toMap(ProductPropertyValueDO::getId, row -> row));
        if (result.size() != ids.size()) { throw exception(PROPERTY_VALUE_NOT_EXISTS); }
        return result;
    }

    private List<ProductSkuSaveReqVO.Property> normalize(List<ProductSkuSaveReqVO.Property> items,
            Map<Long, ProductPropertyDO> propertyMap, Map<Long, ProductPropertyValueDO> valueMap, boolean requireEnabled) {
        List<ProductSkuSaveReqVO.Property> result = new ArrayList<>();
        for (ProductSkuSaveReqVO.Property item : items) {
            ProductPropertyDO property = propertyMap.get(item.getPropertyId());
            ProductPropertyValueDO value = valueMap.get(item.getValueId());
            if (property == null || value == null || !Objects.equals(property.getId(), value.getPropertyId())) {
                throw exception0(400, "所选属性值不属于该属性");
            }
            if (requireEnabled && (!CommonStatusEnum.isEnable(property.getStatus()) || !CommonStatusEnum.isEnable(value.getStatus()))) {
                throw exception0(400, "启用SKU或展示属性不能使用停用的属性项或值");
            }
            result.add(new ProductSkuSaveReqVO.Property(property.getId(), property.getName(), value.getId(), value.getName()));
        }
        result.sort(Comparator.comparing(ProductSkuSaveReqVO.Property::getPropertyId));
        return result;
    }

    private List<ProductSkuDO.Property> toProperties(List<ProductSkuSaveReqVO.Property> source) {
        return source.stream().map(item -> new ProductSkuDO.Property(item.getPropertyId(), item.getPropertyName(),
                item.getValueId(), item.getValueName())).collect(Collectors.toList());
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public void createSkuList(Long spuId,List<ProductSkuSaveReqVO> input) {
        transactions.execute("product:spu:create",actor -> {
            ProductSpuDO spu=writeParent(spuId);
            if (!Long.valueOf(0).equals(spu.getAuthVersion()) || !Integer.valueOf(0).equals(spu.getStatus())
                    || !Objects.equals(spu.getOwnerUserId(),actor.getId()) || !mapper.lockBySpuId(spuId).isEmpty()) {
                throw exception0(409,"只能为本次创建的下架商品初始化SKU");
            }
            validateSkuList(input,spu.getSpecType());
            if (input.stream().anyMatch(row -> row.getId()!=null)) { throw exception0(400,"新商品不能指定已有SKU编号"); }
            bindImages(spuId,input);
            Map<Long,Integer> initial=new LinkedHashMap<>();
            for (ProductSkuSaveReqVO body:input) { insert(spuId,body,actor,initial); }
            stocks.initialize(spuId,initial);
            summarize(spu,actor,0);
            finalAccess(spu,"product:spu:create");
            return null;
        });
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public void updateSkuList(Long spuId,Long expectedSpuVersion,List<ProductSkuSaveReqVO> input) {
        transactions.execute("product:spu:update",actor -> {
            ProductSpuDO spu=writeParent(spuId);
            transactions.requireVersion(spu.getAuthVersion(),expectedSpuVersion);
            if (Integer.valueOf(-1).equals(spu.getStatus())) { throw exception0(409,"请先将回收站商品恢复为下架再编辑"); }
            validateSkuList(input,spu.getSpecType());
            List<ProductSkuDO> current=mapper.lockBySpuId(spuId);
            Map<Long,ProductSkuDO> remaining=current.stream().collect(Collectors.toMap(ProductSkuDO::getId,row -> row));
            Map<String,ProductSkuDO> combinations=new HashMap<>();
            for (ProductSkuDO row:current) {
                if (!Objects.equals(row.getPropertyKey(),ProductSkuConvert.buildPropertyKey(row))
                        || combinations.put(row.getPropertyKey(),row)!=null) {
                    throw new IllegalStateException("已有SKU组合身份不一致");
                }
            }
            // 所有归属/版本/组合先核对，避免明显错误请求先留下远程图片引用。
            for (ProductSkuSaveReqVO body:input) {
                ProductSkuDO proposed=metadata(body);
                if (body.getId()==null) {
                    if (combinations.containsKey(proposed.getPropertyKey())) { throw exception0(409,"该组合已有稳定SKU，请携带原编号和版本更新"); }
                } else {
                    ProductSkuDO old=remaining.get(body.getId());
                    if (old==null) { throw exception0(404,"SKU不存在或不属于当前商品"); }
                    transactions.requireVersion(old.getAuthVersion(),body.getExpectedVersion());
                    if (!old.getPropertyKey().equals(proposed.getPropertyKey())) { throw exception0(400,"不能在原SKU编号上更换规格组合，请新增组合并停用旧SKU"); }
                }
            }
            bindImages(spuId,input);
            Map<Long,Integer> initial=new LinkedHashMap<>();
            for (ProductSkuSaveReqVO body:input) {
                if (body.getId()==null) { insert(spuId,body,actor,initial); continue; }
                ProductSkuDO old=remaining.remove(body.getId()),patch=metadata(body);
                if (mapper.updateMetadata(actor.getTenantId(),spuId,old.getId(),body.getExpectedVersion(),patch,
                        JsonUtils.toJsonString(patch.getProperties()),actor.getId().toString())!=1) {
                    throw exception0(409,"SKU资料已变化，请重新读取");
                }
                transactions.audit(actor,"SKU.UPDATE",old.getId(),old.getAuthVersion(),old.getAuthVersion()+1,
                        Collections.singletonMap("status",patch.getStatus()));
            }
            // 旧SKU不删除、不改组合、不丢弃库存和销量；后续历史订单仍能定位原编号。
            for (ProductSkuDO old:remaining.values()) {
                if (!Integer.valueOf(0).equals(old.getStatus())) { continue; }
                transactions.requireVersion(old.getAuthVersion(),old.getAuthVersion());
                if (mapper.retire(actor.getTenantId(),spuId,old.getId(),old.getAuthVersion(),actor.getId().toString())!=1) {
                    throw exception0(409,"旧SKU资料已变化，请重新读取");
                }
                transactions.audit(actor,"SKU.STATUS",old.getId(),old.getAuthVersion(),old.getAuthVersion()+1,
                        Collections.singletonMap("status",1));
            }
            stocks.initialize(spuId,initial);
            summarize(spu,actor,1);
            finalAccess(spu,"product:spu:update");
            return null;
        });
    }

    private ProductSpuDO writeParent(Long id) {
        ProductSpuDO spu=id==null || id<=0?null:spus.selectByIdForUpdate(id);
        if (spu==null) { throw exception0(404,"商品不存在或不在可管理范围内"); }
        requireScope(spu);
        return spu;
    }
    private void requireScope(ProductSpuDO spu) {
        if (!remote.currentDataScope().includes(spu.getOwnerUserId(),spu.getOwnerDeptId())) {
            throw exception0(404,"商品不存在或不在可管理范围内");
        }
    }
    private void finalAccess(ProductSpuDO spu,String permission) {
        if (!security.hasPermission(permission)) { throw exception0(403,"商品操作权限已改变"); }
        remote.checkTenantOperation();requireScope(spu);
    }
    private void bindImages(Long spuId,List<ProductSkuSaveReqVO> input) {
        for (String url:input.stream().map(ProductSkuSaveReqVO::getPicUrl).collect(Collectors.toCollection(LinkedHashSet::new))) {
            files.bind("SPU",spuId,url);
        }
    }
    private ProductSkuDO metadata(ProductSkuSaveReqVO body) {
        ProductSkuDO row=new ProductSkuDO();
        row.setProperties(body.getProperties().stream().map(item -> new ProductSkuDO.Property(item.getPropertyId(),item.getPropertyName(),
                item.getValueId(),item.getValueName())).collect(Collectors.toList()));
        row.setPropertyKey(ProductSkuConvert.buildPropertyKey(row));
        row.setPrice(body.getPrice());row.setMarketPrice(body.getMarketPrice()==null?0:body.getMarketPrice());
        row.setCostPrice(body.getCostPrice()==null?0:body.getCostPrice());row.setBarCode(body.getBarCode()==null?"":body.getBarCode().trim());
        row.setPicUrl(body.getPicUrl());row.setWeight(body.getWeight());row.setVolume(body.getVolume());row.setStatus(body.getStatus());
        return row;
    }
    private void insert(Long spuId,ProductSkuSaveReqVO body,LoginUser actor,Map<Long,Integer> initial) {
        ProductSkuDO row=metadata(body);row.setSpuId(spuId);row.setStock(0);row.setSalesCount(0);row.setAuthVersion(0L);
        transactions.created(row,actor);
        if (mapper.insert(row)!=1 || row.getId()==null) { throw new IllegalStateException("新SKU保存失败"); }
        initial.put(row.getId(),body.getStock());
        transactions.audit(actor,"SKU.CREATE",row.getId(),-1L,0L,Collections.singletonMap("propertyKey",row.getPropertyKey()));
    }
    private void summarize(ProductSpuDO spu,LoginUser actor,int versionStep) {
        ProductSpuSkuSummary summary=mapper.summarize(actor.getTenantId(),spu.getId());
        if (summary==null || summary.getStock()==null || summary.getStock()<0 || summary.getStock()>Integer.MAX_VALUE) {
            throw exception0(409,"商品可售总库存超出上限");
        }
        if (Integer.valueOf(1).equals(spu.getStatus()) && (summary.getEnabledCount()==null || summary.getEnabledCount()==0)) {
            throw exception0(400,"上架商品必须保留至少一个启用SKU");
        }
        if (spus.updateSkuSummary(actor.getTenantId(),spu.getId(),summary,spu.getAuthVersion(),versionStep,actor.getId().toString())!=1) {
            throw exception0(409,"商品资料已变化，请重新读取");
        }
    }

    @Override
    public ProductSkuDO getSku(Long id) {
        transactions.actor();
        ProductSkuDO row = id == null || id <= 0 ? null : mapper.selectById(id);
        if (row == null) { throw exception(SKU_NOT_EXISTS); }
        ProductSpuDO parent=spus.selectById(row.getSpuId());
        if (parent==null) { throw exception(SKU_NOT_EXISTS); }
        requireScope(parent);
        return row;
    }

    @Override
    public List<ProductSkuDO> getSkuListBySpuId(Long spuId) {
        transactions.actor();
        if (spuId == null || spuId <= 0) { throw exception0(400, "商品编号不正确"); }
        ProductSpuDO parent=spus.selectById(spuId);
        if (parent==null) { throw exception0(404,"商品不存在或不在可管理范围内"); }
        requireScope(parent);
        return mapper.selectListBySpuId(spuId);
    }
}
