package cn.qinghe.mall.module.product.service.spu;

import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.*;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.convert.sku.ProductSkuConvert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.stream.Collectors;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.framework.delivery.ProductDeliveryService;
import cn.qinghe.mall.module.product.framework.file.ProductFileService;
import cn.qinghe.mall.module.product.service.brand.ProductBrandService;
import cn.qinghe.mall.module.product.service.category.ProductCategoryService;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import cn.qinghe.mall.module.product.service.sku.ProductSkuService;
import cn.qinghe.mall.module.trade.api.delivery.dto.DeliveryExpressTemplateCheckRespDTO;
import org.springframework.stereotype.Service;
import javax.validation.Validator;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 直接择取yudao ProductSpuServiceImpl.createSpu/updateSpu流程：校验分类品牌/SKU、插SPU、写SKU。
 * 本项目增加真实模板/图片/数据范围及版本审计；首次下架，不用表单改库存/销量/归属。
 * @author 芋道源码
 */
@Service
public class ProductSpuServiceImpl implements ProductSpuService {
    private final ProductSpuMapper mapper;
    private final ProductSkuService skus;
    private final ProductCategoryService categories;
    private final ProductBrandService brands;
    private final ProductCatalogTransactions transactions;
    private final ProductFileService files;
    private final ProductDeliveryService delivery;
    private final RemoteSystemSecurityClient remote;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final ProductSkuMapper skuMapper;
    private final ProductSkuProperties settings;
    public ProductSpuServiceImpl(ProductSpuMapper mapper,ProductSkuService skus,ProductCategoryService categories,
            ProductBrandService brands,ProductCatalogTransactions transactions,ProductFileService files,
            ProductDeliveryService delivery,RemoteSystemSecurityClient remote,SecurityFrameworkService security,Validator validator,ProductSkuMapper skuMapper,ProductSkuProperties settings) {
        this.mapper=mapper;this.skus=skus;this.categories=categories;this.brands=brands;this.transactions=transactions;
        this.files=files;this.delivery=delivery;this.remote=remote;this.security=security;this.validator=validator;
        this.skuMapper=skuMapper;this.settings=settings;
    }

    @Override
    public ProductOwnerRespDTO getOwnerOption(Long userId) {
        transactions.actor();
        if (!security.hasPermission("product:spu:transfer")) { throw exception0(403,"没有商品归属转移权限"); }
        return remote.productOwner(userId);
    }

    @Override
    public void transferOwner(ProductSpuTransferReqVO body) {
        if (body==null || !validator.validate(body).isEmpty() || body.getReason().trim().isEmpty()) {
            throw exception0(400,"请填写商品、目标员工、当前版本和转移原因");
        }
        transactions.execute("product:spu:transfer",actor -> {
            ProductSpuDO row=mapper.selectByIdForUpdate(body.getId());requireVisible(row);
            transactions.requireVersion(row.getAuthVersion(),body.getExpectedVersion());
            ProductOwnerRespDTO target=remote.productOwner(body.getTargetUserId());
            if (!Objects.equals(target.getUserVersion(),body.getExpectedOwnerVersion())) {
                throw exception0(409,"目标员工资料已变化，请重新核对归属");
            }
            if (Objects.equals(row.getOwnerUserId(),target.getOwnerUserId()) && Objects.equals(row.getOwnerDeptId(),target.getOwnerDeptId())) {
                return null;
            }
            if (mapper.transferOwner(actor.getTenantId(),row.getId(),row.getAuthVersion(),target.getOwnerUserId(),target.getOwnerDeptId(),actor.getId().toString())!=1) {
                throw exception0(409,"商品归属已变化，请重新读取");
            }
            Map<String,Object> details=new LinkedHashMap<>();details.put("reason",body.getReason().trim());
            details.put("fromOwnerUserId",row.getOwnerUserId());details.put("fromOwnerDeptId",row.getOwnerDeptId());
            details.put("toOwnerUserId",target.getOwnerUserId());details.put("toOwnerDeptId",target.getOwnerDeptId());
            details.put("targetUserVersion",target.getUserVersion());details.put("targetDeptVersion",target.getDeptVersion());
            transactions.audit(actor,"SPU.TRANSFER",row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);
            finalIdentity(row,"product:spu:transfer");
            ProductOwnerRespDTO latest=remote.productOwner(target.getOwnerUserId());
            if (!Objects.equals(target.getUserVersion(),latest.getUserVersion()) || !Objects.equals(target.getOwnerDeptId(),latest.getOwnerDeptId())
                    || !Objects.equals(target.getDeptVersion(),latest.getDeptVersion())) {
                throw exception0(409,"目标员工或部门已变化，请重新核对归属");
            }
            return null;
        });
    }

    @Override
    public Long createSpu(ProductSpuSaveReqVO body) {
        validateInput(body);
        if (body.getId()!=null || body.getExpectedVersion()!=null || body.getSkus().stream().anyMatch(sku -> sku.getId()!=null)) {
            throw exception0(400,"新商品不能指定商品/SKU编号或资料版本");
        }
        return transactions.execute("product:spu:create",actor -> {
            DeliveryExpressTemplateCheckRespDTO template=validateReferences(body);
            UserDataScopeRespDTO scope=remote.currentDataScope();
            ProductSpuDO row=metadata(body);
            row.setOwnerUserId(actor.getId());row.setOwnerDeptId(scope.getCurrentDeptId());
            if (!scope.includes(row.getOwnerUserId(),row.getOwnerDeptId())) { throw exception0(403,"新商品归属不在当前数据范围内"); }
            row.setStatus(0);row.setPrice(0);row.setMarketPrice(0);row.setCostPrice(0);row.setStock(0);
            row.setSalesCount(0);row.setBrowseCount(0);row.setAuthVersion(0L);
            transactions.created(row,actor);
            if (mapper.insert(row)!=1 || row.getId()==null) { throw new IllegalStateException("商品保存失败"); }
            bindImages(row.getId(),body);
            skus.createSkuList(row.getId(),body.getSkus());
            transactions.audit(actor,"SPU.CREATE",row.getId(),-1L,0L,details(body,template));
            finalAccess(row,body,template,"product:spu:create");
            return row.getId();
        });
    }

    @Override
    public void updateSpu(ProductSpuSaveReqVO body) {
        validateInput(body);
        if (body.getId()==null) { throw exception0(400,"请提供商品编号"); }
        transactions.execute("product:spu:update",actor -> {
            ProductSpuDO current=mapper.selectByIdForUpdate(body.getId());
            requireVisible(current);
            transactions.requireVersion(current.getAuthVersion(),body.getExpectedVersion());
            if (Integer.valueOf(-1).equals(current.getStatus())) { throw exception0(409,"请先恢复回收站商品再编辑"); }
            DeliveryExpressTemplateCheckRespDTO template=validateReferences(body);
            ProductSpuDO patch=metadata(body);
            bindImages(current.getId(),body);
            // 先保存specType以便SKU按新规格类型校验；仍在同事务，任何后续失败均回滚。
            if (mapper.updateSaveFields(actor.getTenantId(),current.getId(),current.getAuthVersion(),patch,
                    JsonUtils.toJsonString(patch.getSliderPicUrls()),JsonUtils.toJsonString(patch.getDisplayProperties()),actor.getId().toString())!=1) {
                throw exception0(409,"商品资料已变化，请重新读取");
            }
            // SKU差集/库存初始/汇总在同事务；该方法统一递增父版本一次。
            skus.updateSkuList(current.getId(),current.getAuthVersion(),body.getSkus());
            transactions.audit(actor,"SPU.UPDATE",current.getId(),current.getAuthVersion(),current.getAuthVersion()+1,details(body,template));
            finalAccess(current,body,template,"product:spu:update");
            return null;
        });
    }

    @Override
    public ProductSpuDO getSpu(Long id) {
        transactions.actor();
        if (!security.hasPermission("product:spu:query")) { throw exception0(403,"没有商品查询权限"); }
        ProductSpuDO row=id==null || id<=0?null:mapper.selectById(id);
        requireVisible(row);return row;
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public ProductSpuRespVO getSpuDetail(Long id) {
        ProductSpuDO row=getSpu(id);
        ProductSpuRespVO result=BeanUtils.toBean(row,ProductSpuRespVO.class);
        result.setSkus(BeanUtils.toBean(skuMapper.selectListBySpuId(id),ProductSkuRespVO.class));
        return result;
    }

    @Override
    public PageResult<ProductSpuDO> getSpuPage(ProductSpuPageReqVO body) {
        requireQuery();validatePage(body);
        return mapper.selectPage(body,remote.currentDataScope(),settings.getAlertStock());
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public Map<Integer,Long> getTabsCount(ProductSpuPageReqVO body) {
        requireQuery();validatePage(body);UserDataScopeRespDTO scope=remote.currentDataScope();
        Map<Integer,Long> counts=new LinkedHashMap<>();
        for (int tab=0;tab<=4;tab++) { counts.put(tab,mapper.selectCountByTab(body,scope,tab,settings.getAlertStock())); }
        return counts;
    }

    @Override
    public void updateSpuStatus(ProductSpuUpdateStatusReqVO body) {
        if (body==null || !validator.validate(body).isEmpty()) { throw exception0(400,"商品状态参数不正确"); }
        transactions.execute("product:spu:update",actor -> {
            ProductSpuDO row=mapper.selectByIdForUpdate(body.getId());requireVisible(row);
            transactions.requireVersion(row.getAuthVersion(),body.getExpectedVersion());
            int current=row.getStatus(),next=body.getStatus();
            if (current==next) { return null; }
            if ((current==1 && next==-1) || (current==-1 && next==1)) {
                throw exception0(409,"请先切换为下架状态，再执行上架或移入回收站");
            }
            ProductSpuSaveReqVO checked=null;DeliveryExpressTemplateCheckRespDTO template=null;
            if (next==1) { checked=publicationSnapshot(row);template=validateReferences(checked);bindImages(row.getId(),checked); }
            if (mapper.updateStatus(actor.getTenantId(),row.getId(),row.getAuthVersion(),current,next,actor.getId().toString())!=1) {
                throw exception0(409,"商品状态已变化，请重新读取");
            }
            Map<String,Object> details=new LinkedHashMap<>();details.put("fromStatus",current);details.put("toStatus",next);
            if (template!=null) { details.put("deliveryTemplateId",template.getId());details.put("deliveryTemplateVersion",template.getAuthVersion()); }
            transactions.audit(actor,"SPU.STATUS",row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);
            if (checked!=null) { finalAccess(row,checked,template,"product:spu:update"); }
            else { finalIdentity(row,"product:spu:update"); }
            return null;
        });
    }

    @Override
    public void deleteSpu(Long id,Long expectedVersion) {
        transactions.execute("product:spu:delete",actor -> {
            ProductSpuDO row=id==null || id<=0?null:mapper.selectByIdForUpdate(id);requireVisible(row);
            transactions.requireVersion(row.getAuthVersion(),expectedVersion);
            if (!Integer.valueOf(-1).equals(row.getStatus())) { throw exception0(409,"只有回收站商品可以删除"); }
            // 保留稳定SKU和库存/销量记录；旧订单/未来可信补偿必须按历史引用读取，不能复用原编号。
            if (mapper.retire(actor.getTenantId(),row.getId(),row.getAuthVersion(),actor.getId().toString())!=1) { throw exception0(409,"商品已变化，请重新读取"); }
            transactions.audit(actor,"SPU.DELETE",row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,Collections.singletonMap("skuHistoryRetained",true));
            finalIdentity(row,"product:spu:delete");return null;
        });
    }

    private void requireQuery() {
        transactions.actor();if (!security.hasPermission("product:spu:query")) { throw exception0(403,"没有商品查询权限"); }
    }
    private void validatePage(ProductSpuPageReqVO body) {
        if (body==null || !validator.validate(body).isEmpty()) { throw exception0(400,"商品分页参数不正确"); }
    }
    private void finalIdentity(ProductSpuDO row,String permission) {
        if (!security.hasPermission(permission)) { throw exception0(403,"商品操作权限已改变"); }
        remote.checkTenantOperation();requireVisible(row);
    }
    /** 只读取服务器已保存的启用SKU重验发布条件，库存只读、不从外部请求重写。 */
    private ProductSpuSaveReqVO publicationSnapshot(ProductSpuDO row) {
        List<ProductSkuDO> enabled=skuMapper.lockBySpuId(row.getId()).stream().filter(sku -> Integer.valueOf(0).equals(sku.getStatus())).collect(Collectors.toList());
        if (enabled.isEmpty()) { throw exception0(400,"上架至少需要一条启用SKU"); }
        ProductSpuSaveReqVO body=BeanUtils.toBean(row,ProductSpuSaveReqVO.class);
        body.setExpectedVersion(row.getAuthVersion());
        List<ProductSkuSaveReqVO> snapshots=new ArrayList<>();
        for (ProductSkuDO sku:enabled) {
            if (sku.getStock()==null || sku.getStock()<0 || !Objects.equals(sku.getPropertyKey(),ProductSkuConvert.buildPropertyKey(sku))) {
                throw exception0(400,"SKU库存或规格身份不正确");
            }
            ProductSkuSaveReqVO snapshot=BeanUtils.toBean(sku,ProductSkuSaveReqVO.class);
            snapshot.setStock(null);snapshot.setExpectedVersion(sku.getAuthVersion());snapshots.add(snapshot);
        }
        body.setSkus(snapshots);validateInput(body);
        skus.validateDisplayProperties(body.getDisplayProperties());
        // 图像绑定仍必须走真实infra；已保存SKU图也逐一核对，不能只检查封面。
        for (String url:snapshots.stream().map(ProductSkuSaveReqVO::getPicUrl).collect(Collectors.toCollection(LinkedHashSet::new))) { files.bind("SPU",row.getId(),url); }
        ProductSpuSkuSummary summary=skuMapper.summarize(row.getTenantId(),row.getId());
        if (summary==null || summary.getStock()==null || summary.getStock()>Integer.MAX_VALUE
                || !Objects.equals(summary.getStock(),row.getStock().longValue()) || !Objects.equals(summary.getPrice(),row.getPrice().longValue())
                || !Objects.equals(summary.getMarketPrice(),row.getMarketPrice().longValue()) || !Objects.equals(summary.getCostPrice(),row.getCostPrice().longValue())) {
            throw exception0(409,"商品价格或库存汇总不一致，请重新保存商品");
        }
        return body;
    }

    private void validateInput(ProductSpuSaveReqVO body) {
        if (body==null || !validator.validate(body).isEmpty()) { throw exception0(400,"商品字段不完整或不符合范围要求"); }
    }
    private DeliveryExpressTemplateCheckRespDTO validateReferences(ProductSpuSaveReqVO body) {
        categories.validateCategory(body.getCategoryId());brands.validateProductBrand(body.getBrandId());
        skus.validateSkuList(body.getSkus(),body.getSpecType());
        // 展示属性也实际核对，在metadata中再次生成可信快照；不进入SKU组合。
        return delivery.validate(body.getDeliveryTemplateId(),body.getSkus());
    }
    private ProductSpuDO metadata(ProductSpuSaveReqVO body) {
        ProductSpuDO row=BeanUtils.toBean(body,ProductSpuDO.class);
        row.setId(null);row.setName(body.getName().trim());row.setKeyword(body.getKeyword()==null?"":body.getKeyword().trim());
        row.setIntroduction(body.getIntroduction()==null?"":body.getIntroduction());
        row.setSliderPicUrls(new ArrayList<>(body.getSliderPicUrls()));
        row.setDisplayProperties(skus.validateDisplayProperties(body.getDisplayProperties()));
        return row;
    }
    private void bindImages(Long id,ProductSpuSaveReqVO body) {
        Set<String> images=new LinkedHashSet<>(body.getSliderPicUrls());images.add(body.getPicUrl());
        for (String url:images) { files.bind("SPU",id,url); }
    }
    private void requireVisible(ProductSpuDO row) {
        if (row==null || !remote.currentDataScope().includes(row.getOwnerUserId(),row.getOwnerDeptId())) {
            throw exception0(404,"商品不存在或不在可管理范围内");
        }
    }
    private void finalAccess(ProductSpuDO row,ProductSpuSaveReqVO body,DeliveryExpressTemplateCheckRespDTO checked,String permission) {
        if (!security.hasPermission(permission)) { throw exception0(403,"商品操作权限已改变"); }
        remote.checkTenantOperation();requireVisible(row);
        DeliveryExpressTemplateCheckRespDTO latest=delivery.validate(body.getDeliveryTemplateId(),body.getSkus());
        if (!Objects.equals(checked.getAuthVersion(),latest.getAuthVersion())) { throw exception0(409,"配送模板已变化，请重新确认后保存"); }
    }
    private Map<String,Object> details(ProductSpuSaveReqVO body,DeliveryExpressTemplateCheckRespDTO template) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("categoryId",body.getCategoryId());result.put("brandId",body.getBrandId());result.put("skuCount",body.getSkus().size());
        result.put("deliveryTemplateId",template.getId());result.put("deliveryTemplateVersion",template.getAuthVersion());
        result.put("specType",body.getSpecType());return result;
    }
}
