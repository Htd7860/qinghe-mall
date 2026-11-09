package cn.qinghe.mall.module.product.service.stock;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductStockAdjustReqVO;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductStockOperationRespVO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary;
import cn.qinghe.mall.module.product.dal.dataobject.stock.*;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.dal.mysql.stock.ProductStockMapper;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import cn.qinghe.mall.module.product.service.search.ProductSearchChanges;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.validation.Validator;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * yudao updateSkuStock的条件增减流程，按B04加已提交结果/幂等键与前后值流水。
 * 这里只接初始和手工调整；订单/取消/退款/秒杀不应直接调用手工调整冒充自己的业务协议。
 * @author 芋道源码
 */
@Service
public class ProductStockService {
    private final ProductStockMapper ledger;
    private final ProductSkuMapper skus;
    private final ProductSpuMapper spus;
    private final ProductCatalogTransactions transactions;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final ProductSkuProperties settings;
    private final Validator validator;
    private final ProductSearchChanges searchChanges;

    public ProductStockService(ProductStockMapper ledger,ProductSkuMapper skus,ProductSpuMapper spus,
            ProductCatalogTransactions transactions,SecurityFrameworkService security,
            RemoteSystemSecurityClient remote,ProductSkuProperties settings,Validator validator,ProductSearchChanges searchChanges) {
        this.ledger=ledger;this.skus=skus;this.spus=spus;this.transactions=transactions;
        this.security=security;this.remote=remote;this.settings=settings;this.validator=validator;
        this.searchChanges=searchChanges;
    }

    /** 新SKU同一保存事务内先以0插入，再逐条记初始量；0初始量也有唯一流水。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void initialize(Long spuId,Map<Long,Integer> initial) {
        MybatisUtils.requireWriteTransaction();
        LoginUser actor=transactions.actor();
        if (initial==null || initial.isEmpty()) { return; }
        if (initial.size()>settings.getMaxSkus() || initial.entrySet().stream().anyMatch(item ->
                item.getKey()==null || item.getKey()<=0 || item.getValue()==null || item.getValue()<0)) {
            throw exception0(400,"初始库存参数不正确");
        }
        if (!security.hasAnyPermissions("product:spu:create","product:spu:update")) { throw exception0(403,"没有商品保存权限"); }
        ProductSpuDO spu=parent(spuId);
        remote.checkTenantOperation(); requireScope(spu);
        TreeMap<Long,Integer> ordered=new TreeMap<>(initial);
        String key="sku-init:"+DigestUtil.sha256Hex(ordered.keySet().stream().map(String::valueOf).collect(Collectors.joining(",")));
        perform(actor,spu,key,"INITIALIZE","SKU初始库存",ordered);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=15)
    public ProductStockOperationRespVO adjust(ProductStockAdjustReqVO body) {
        LoginUser actor=transactions.actor();
        if (body==null || !validator.validate(body).isEmpty() || body.getItems().size()>settings.getMaxSkus()) {
            throw exception0(400,"库存调整参数不正确");
        }
        requirePermission();
        ProductSpuDO spu=parent(body.getSpuId());
        remote.checkTenantOperation(); requireScope(spu);
        TreeMap<Long,Integer> changes=new TreeMap<>();
        for (ProductStockAdjustReqVO.Item item:body.getItems()) {
            if (item.getDelta()==0 || changes.put(item.getSkuId(),item.getDelta())!=null) {
                throw exception0(400,"每个SKU只允许一个非零库存增减量");
            }
        }
        ProductStockOperationRespVO result=perform(actor,spu,"manual:"+body.getOperationKey(),"ADJUST",body.getReason().trim(),changes);
        // 提交前再读资格；不是和system组成跨库原子事务，仍需后续撤权/并发验收。
        requirePermission(); remote.checkTenantOperation(); requireScope(spu);
        return result;
    }

    /** 只查已提交结果；空结果可能对应仍未提交的请求，不能换业务键重复加减。 */
    public ProductStockOperationRespVO getOperation(Long spuId,String operationKey) {
        LoginUser actor=transactions.actor(); requirePermission();
        if (operationKey==null || !operationKey.matches("[A-Za-z0-9][A-Za-z0-9:_-]{7,79}")) {
            throw exception0(400,"库存业务键不正确");
        }
        ProductSpuDO spu=spuId==null || spuId<=0?null:spus.selectStockOwnerIncludingDeleted(actor.getTenantId(),spuId);
        if (spu==null) { throw exception0(404,"商品不存在或不在可管理范围内"); }
        requireScope(spu);
        ProductStockOperationDO operation=ledger.getOperation(actor.getTenantId(),"manual:"+operationKey);
        if (operation==null) { throw exception0(404,"暂未查到已提交结果，请保持原业务键稍后查询或重试"); }
        if (!Objects.equals(operation.getSpuId(),spuId) || !Objects.equals(operation.getActorUserId(),actor.getId())) {
            throw exception0(404,"库存操作不存在或不属于当前操作人");
        }
        return result(operation);
    }

    private ProductStockOperationRespVO perform(LoginUser actor,ProductSpuDO spu,String key,String kind,
            String reason,TreeMap<Long,Integer> changes) {
        MybatisUtils.requireWriteTransaction();
        Map<String,Object> canonical=new TreeMap<>();
        canonical.put("tenantId",actor.getTenantId()); canonical.put("actorId",actor.getId());
        canonical.put("spuId",spu.getId()); canonical.put("kind",kind); canonical.put("reason",reason);
        canonical.put("changes",changes);
        String hash=DigestUtil.sha256Hex(JsonUtils.toJsonString(canonical));
        ProductStockOperationDO seed=new ProductStockOperationDO();
        seed.setTenantId(actor.getTenantId());seed.setActorUserId(actor.getId());seed.setOperationKey(key);
        seed.setKind(kind);seed.setSpuId(spu.getId());seed.setRequestHash(hash);seed.setReason(reason);
        int created=ledger.ensureOperation(seed);
        ProductStockOperationDO operation=ledger.lockOperation(actor.getTenantId(),key);
        if (operation==null) { throw new IllegalStateException("库存操作登记失败"); }
        if (!hash.equals(operation.getRequestHash()) || !Objects.equals(operation.getSpuId(),spu.getId())
                || !kind.equals(operation.getKind()) || !Objects.equals(operation.getActorUserId(),actor.getId())) {
            throw exception0(409,"该库存业务键已用于其他参数，不能重复使用");
        }
        if (created==0) {
            ProductStockOperationRespVO previous=result(operation);
            if (previous.getItems().size()!=changes.size()) { throw new IllegalStateException("库存操作记录不完整"); }
            return previous;
        }
        if (created!=1) { throw new IllegalStateException("库存操作登记数量异常"); }
        List<ProductSkuDO> locked=skus.lockByIdsAndSpuId(spu.getId(),changes.keySet());
        if (locked.size()!=changes.size()) { throw exception0(404,"SKU不存在或不属于当前商品"); }
        for (ProductSkuDO sku:locked) {
            int delta=changes.get(sku.getId()),before=sku.getStock();
            if ("INITIALIZE".equals(kind) && (before!=0 || !Long.valueOf(0).equals(sku.getAuthVersion()) || sku.getSalesCount()!=0)) {
                throw exception0(409,"只能初始化尚未使用的新SKU");
            }
            long after=(long)before+delta;
            if (after<0 || after>Integer.MAX_VALUE) { throw exception0(409,"库存不足或调整后超出上限"); }
            if (delta!=0 && ledger.changeStock(actor.getTenantId(),spu.getId(),sku.getId(),delta,actor.getId().toString())!=1) {
                throw exception0(409,"库存不足或已变化，请查询原操作结果");
            }
            ProductStockRecordDO line=new ProductStockRecordDO();
            line.setTenantId(actor.getTenantId());line.setOperationId(operation.getId());line.setSpuId(spu.getId());
            line.setSkuId(sku.getId());line.setKind(kind);line.setDelta(delta);line.setBeforeStock(before);line.setAfterStock((int)after);
            if (ledger.insertRecord(line)!=1) { throw new IllegalStateException("库存流水保存失败"); }
        }
        ProductSpuSkuSummary summary=skus.summarize(actor.getTenantId(),spu.getId());
        if (summary==null || summary.getStock()==null || summary.getStock()<0 || summary.getStock()>Integer.MAX_VALUE) {
            throw exception0(409,"商品可售总库存超出上限");
        }
        if (ledger.updateSpuStock(actor.getTenantId(),spu.getId(),summary.getStock().intValue(),actor.getId().toString())!=1) {
            throw new IllegalStateException("商品库存汇总更新失败");
        }
        // 重复操作在前面返回；SKU初始量由外层商品保存事务统一标记。
        if ("ADJUST".equals(kind)) { searchChanges.mark(actor.getTenantId()); }
        return result(operation);
    }

    private ProductSpuDO parent(Long id) {
        ProductSpuDO row=id==null || id<=0?null:spus.selectByIdForUpdate(id);
        if (row==null) { throw exception0(404,"商品不存在或不在可管理范围内"); }
        return row;
    }
    private void requirePermission() {
        if (!security.hasPermission("product:spu:update-stock")) { throw exception0(403,"没有库存调整权限"); }
    }
    private void requireScope(ProductSpuDO spu) {
        if (!remote.currentDataScope().includes(spu.getOwnerUserId(),spu.getOwnerDeptId())) {
            throw exception0(404,"商品不存在或不在可管理范围内");
        }
    }
    private ProductStockOperationRespVO result(ProductStockOperationDO operation) {
        List<ProductStockRecordDO> records=ledger.getRecords(operation.getTenantId(),operation.getId());
        if (records.isEmpty()) { throw new IllegalStateException("库存操作缺少明细"); }
        ProductStockOperationRespVO result=new ProductStockOperationRespVO();
        result.setId(operation.getId());result.setKind(operation.getKind());result.setSpuId(operation.getSpuId());
        result.setOperationKey(operation.getOperationKey().startsWith("manual:")?operation.getOperationKey().substring(7):operation.getOperationKey());
        result.setCreateTime(operation.getCreateTime());
        result.setItems(records.stream().map(record -> {
            if (!Objects.equals(record.getSpuId(),operation.getSpuId()) || !Objects.equals(record.getKind(),operation.getKind())) {
                throw new IllegalStateException("库存明细归属异常");
            }
            ProductStockOperationRespVO.Item item=new ProductStockOperationRespVO.Item();
            item.setSkuId(record.getSkuId());item.setDelta(record.getDelta());item.setBeforeStock(record.getBeforeStock());item.setAfterStock(record.getAfterStock());
            return item;
        }).collect(Collectors.toList()));
        return result;
    }
}
