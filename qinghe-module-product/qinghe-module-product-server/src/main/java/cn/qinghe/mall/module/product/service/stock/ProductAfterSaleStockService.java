package cn.qinghe.mall.module.product.service.stock;

import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.stock.dto.*;
import cn.qinghe.mall.module.product.dal.dataobject.stock.*;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary;
import cn.qinghe.mall.module.product.dal.mysql.stock.*;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.service.search.ProductSearchChanges;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原updateSkuStock增量回补；复用现有订单库存锁/restore/summarize，按原售后号提交一次。
 * 不校验当前上下架或价格以阻止历史义务；不接受普通用户，调用方须先确认退款及可售验收。
 * @author 芋道源码
 */
@Service @RequiredArgsConstructor
public class ProductAfterSaleStockService {
    private final ProductOrderStockMapper orders;
    private final ProductAfterSaleStockMapper refunds;
    private final ProductSkuMapper skus;
    private final Validator validator;
    private final ProductSearchChanges searchChanges;

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=15,rollbackFor=Exception.class)
    public ProductAfterSaleStockRespDTO restore(ProductAfterSaleStockReqDTO body) {
        Long tenant=validate(body);
        ProductOrderStockDO operation=orders.lock(tenant,body.getOrderId());
        owned(operation,body);
        ProductAfterSaleStockDO old=refunds.get(tenant,body.getAfterSaleId());
        if (old!=null) {same(old,body);return result(old);}
        if (!Integer.valueOf(ProductOrderStockRespDTO.DEDUCTED).equals(operation.getStatus()))
            throw exception0(409,"原订单未扣减或已整单释放，不能售后回补");
        List<ProductOrderStockItemDO> items=orders.items(tenant,operation.getId());
        if (items.isEmpty() || items.size()>200) throw exception0(503,"原订单扣减明细不完整");
        ProductOrderStockItemDO original=items.stream().filter(value -> Objects.equals(value.getOrderItemId(),body.getOrderItemId())).findFirst().orElse(null);
        if (original==null || !Objects.equals(original.getSpuId(),body.getSpuId()) || !Objects.equals(original.getSkuId(),body.getSkuId())
                || original.getReleaseBeforeStock()!=null || original.getReleaseAfterStock()!=null || original.getRestoredCount()==null
                || refunds.restored(tenant,operation.getId(),body.getOrderItemId())!=original.getRestoredCount()
                || (long)original.getRestoredCount()+body.getQuantity()>original.getQuantity())
            throw exception0(409,"原扣减数量、售后累计回补或SKU归属需核对");
        if (orders.lockSpu(tenant,body.getSpuId())==null) throw exception0(409,"原商品缺失，回补需核对");
        ProductSkuDO sku=orders.lockSku(tenant,body.getSpuId(),body.getSkuId());
        if (sku==null) throw exception0(409,"原SKU缺失，回补需核对");
        long after=Math.addExact(sku.getStock().longValue(),body.getQuantity().longValue());
        String actor="after-sale:"+body.getAfterSaleId();
        if (after>Integer.MAX_VALUE || orders.restore(tenant,body.getSpuId(),body.getSkuId(),body.getQuantity(),actor)!=1)
            throw exception0(409,"恢复库存超出上限，原退款保持已确认");
        ProductAfterSaleStockDO row=BeanUtils.toBean(body,ProductAfterSaleStockDO.class);
        row.setTenantId(tenant);row.setOperationId(operation.getId());row.setBeforeStock(sku.getStock());row.setAfterStock((int)after);
        row.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS));
        if (refunds.accumulate(tenant,operation.getId(),body.getOrderItemId(),original.getRestoredCount(),body.getQuantity())!=1 || refunds.insert(row)!=1)
            throw exception0(409,"原售后回补记录已变化，请查询原结果");
        ProductSpuSkuSummary summary=skus.summarize(tenant,body.getSpuId());
        if (summary==null || summary.getStock()==null || summary.getStock()<0 || summary.getStock()>Integer.MAX_VALUE
                || orders.summarize(tenant,body.getSpuId(),summary.getStock().intValue(),actor)!=1)
            throw exception0(409,"原商品库存汇总需核对");
        searchChanges.mark(tenant);
        return result(row);
    }

    /** 纯读原回执，null不表示没有在途回补；调用方仍须按原售后号恢复。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5,readOnly=true)
    public ProductAfterSaleStockRespDTO get(ProductAfterSaleStockReqDTO body) {
        Long tenant=validate(body);owned(orders.get(tenant,body.getOrderId()),body);
        ProductAfterSaleStockDO row=refunds.get(tenant,body.getAfterSaleId());
        if (row==null) return null;
        same(row,body);return result(row);
    }
    private Long validate(ProductAfterSaleStockReqDTO body) {
        if (body==null || !validator.validate(body).isEmpty()) throw exception0(400,"原售后库存身份或数量不正确");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()) throw exception0(403,"回补需要可信订单租户");
        return tenant;
    }
    private void owned(ProductOrderStockDO row,ProductAfterSaleStockReqDTO body) {
        if (row==null || !Objects.equals(row.getUserId(),body.getUserId())) throw exception0(404,"原本人订单扣减不存在");
    }
    private void same(ProductAfterSaleStockDO row,ProductAfterSaleStockReqDTO body) {
        if (!Objects.equals(row.getOrderId(),body.getOrderId()) || !Objects.equals(row.getUserId(),body.getUserId())
                || !Objects.equals(row.getOrderItemId(),body.getOrderItemId()) || !Objects.equals(row.getSpuId(),body.getSpuId())
                || !Objects.equals(row.getSkuId(),body.getSkuId()) || !Objects.equals(row.getQuantity(),body.getQuantity()))
            throw exception0(409,"同一售后不能更换原订单、SKU或回补数量");
    }
    private ProductAfterSaleStockRespDTO result(ProductAfterSaleStockDO row) {
        ProductAfterSaleStockRespDTO result=BeanUtils.toBean(row,ProductAfterSaleStockRespDTO.class);
        result.setRestoredTime(row.getCreateTime());return result;
    }
}
