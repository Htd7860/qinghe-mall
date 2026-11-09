package cn.qinghe.mall.module.product.service.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillReturnReqDTO;
import cn.qinghe.mall.module.product.dal.dataobject.seckill.ProductSeckillAllocationDO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary;
import cn.qinghe.mall.module.product.dal.mysql.seckill.ProductSeckillAllocationMapper;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.dal.mysql.stock.ProductOrderStockMapper;
import cn.qinghe.mall.module.product.service.search.ProductSearchChanges;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.validation.Validator;
import java.util.Objects;
import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO.*;

/**
 * 复用原 SKU 条件扣增及既有 SPU→SKU 锁/汇总；划拨不增加销量。
 * 只供专用 promotion 服务身份经固定内部Controller调用；无网关路由。
 * 发布失败/开始前撤回才可 compensate；不得用于活动开卖后的库存清算或退款。
 * 闭场归还走returnStock累计原目标；划拨不增加销量，归还也不回减销量。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('internal:promotion:seckill-stock')")
public class ProductSeckillAllocationService {
    private final ProductSeckillAllocationMapper allocations;
    private final ProductOrderStockMapper stocks;
    private final ProductSkuMapper skus;
    private final Validator validator;
    private final ProductSearchChanges searchChanges;

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=15,rollbackFor=Exception.class)
    public ProductSeckillAllocationRespDTO allocate(ProductSeckillAllocationReqDTO body) {
        ProductSeckillAllocationDO row=original(body,true);
        // 已关闭的业务键永不重新划拨，即使关闭发生在第一次扣减之前。
        if (row.getStatus()==COMPENSATED || row.getStatus()==ALLOCATED || row.getStatus()==RETURNING || row.getStatus()==RETURNED) return result(row);
        if (row.getStatus()!=PENDING) throw exception0(409,"原发布划拨状态需要核查");
        ProductSpuDO spu=stocks.lockSpu(row.getTenantId(),row.getSpuId());
        if (spu==null || !Boolean.FALSE.equals(spu.getDeleted()) || !Integer.valueOf(1).equals(spu.getStatus())
                || !Objects.equals(spu.getAuthVersion(),row.getSpuVersion()))
            throw exception0(409,"发布商品状态或版本已变化");
        ProductSkuDO sku=stocks.lockSku(row.getTenantId(),row.getSpuId(),row.getSkuId());
        if (sku==null || !Boolean.FALSE.equals(sku.getDeleted()) || !Integer.valueOf(0).equals(sku.getStatus())
                || !Objects.equals(sku.getAuthVersion(),row.getSkuVersion()) || sku.getStock()==null || sku.getStock()<row.getQuantity())
            throw exception0(409,"发布规格状态、版本或可售库存已变化");
        String actor=actor(row);
        if (allocations.deduct(row,actor)!=1 || allocations.allocated(row)!=1)
            throw exception0(409,"库存划拨状态已变化，请查询原业务号");
        journal(row,"ALLOCATE",sku.getStock(),sku.getStock()-row.getQuantity(),"");
        summarize(row,actor);
        row.setAllocatedQuantity(row.getQuantity());row.setStatus(ALLOCATED);row.setVersion(row.getVersion()+1);
        searchChanges.mark(row.getTenantId());
        return result(row);
    }

    /**
     * 调用方必须先持久停止本代发布，确认从未开放准入；按原完整请求发送，不换业务键。
     * 查不到记录也写入关闭凭证，不能把 null 当作不存在迟到划拨的证明。
     */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=15,rollbackFor=Exception.class)
    public ProductSeckillAllocationRespDTO compensate(ProductSeckillAllocationReqDTO body) {
        ProductSeckillAllocationDO row=original(body,true);
        if (row.getStatus()==COMPENSATED) return result(row);
        if ((row.getStatus()!=PENDING && row.getStatus()!=ALLOCATED) || row.getReturnedQuantity()!=0)
            throw exception0(409,"原划拨已有其他清算，不能按发布失败全额补偿");
        if (row.getAllocatedQuantity()>0) {
            // 历史归还不受当前下架/停用/逻辑删除阻止；仍核对原 tenant/SPU/SKU。
            if (stocks.lockSpu(row.getTenantId(),row.getSpuId())==null) throw exception0(409,"原商品缺失，补偿需核查");
            ProductSkuDO sku=stocks.lockSku(row.getTenantId(),row.getSpuId(),row.getSkuId());
            if (sku==null || sku.getStock()==null || (long)sku.getStock()+row.getAllocatedQuantity()>Integer.MAX_VALUE)
                throw exception0(409,"原规格缺失或补偿库存超出上限");
            String actor=actor(row);
            if (stocks.restore(row.getTenantId(),row.getSpuId(),row.getSkuId(),row.getAllocatedQuantity(),actor)!=1)
                throw exception0(409,"库存补偿未完成，请查询原业务号");
            journal(row,"COMPENSATE",sku.getStock(),sku.getStock()+row.getAllocatedQuantity(),"PUBLISH_CANCELLED");
            summarize(row,actor);
        }
        if (allocations.compensated(row)!=1) throw exception0(409,"原发布关闭状态已变化");
        row.setReturnedQuantity(row.getAllocatedQuantity());row.setStatus(COMPENSATED);row.setVersion(row.getVersion()+1);
        // 从未实际划拨的关闭凭证不改变商品库存，也不推进搜索变更。
        if (row.getAllocatedQuantity()>0) searchChanges.mark(row.getTenantId());
        return result(row);
    }

    /** 闭场累计归还；来源promotion必须先持久预留原池可归还数量，不得以此替代发布失败补偿。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=15,rollbackFor=Exception.class)
    public ProductSeckillAllocationRespDTO returnStock(ProductSeckillReturnReqDTO body) {
        if(body==null || !validator.validate(body).isEmpty())throw exception0(400,"需要原划拨及正整数累计归还目标");
        ProductSeckillAllocationDO row=original(body.getAllocation(),true,false); // 缺原账本不能创建或伪造划拨。
        if(row==null)throw exception0(404,"原实际划拨凭证尚未确认");
        if(!Arrays.asList(ALLOCATED,RETURNING,RETURNED).contains(row.getStatus()))throw exception0(409,"原划拨不是可清算状态");
        int target=body.getReturnedQuantity();
        if(target>row.getAllocatedQuantity())throw exception0(409,"累计归还不能超过原实际划拨量");
        if(target<=row.getReturnedQuantity())return result(row); // 重复/较旧目标只读当前累计结果。
        int delta=target-row.getReturnedQuantity();
        if(stocks.lockSpu(row.getTenantId(),row.getSpuId())==null)throw exception0(409,"原商品缺失，归还需核查");
        ProductSkuDO sku=stocks.lockSku(row.getTenantId(),row.getSpuId(),row.getSkuId());
        if(sku==null || sku.getStock()==null || (long)sku.getStock()+delta>Integer.MAX_VALUE)throw exception0(409,"原规格缺失或归还库存超限");
        String actor=actor(row);
        if(stocks.restore(row.getTenantId(),row.getSpuId(),row.getSkuId(),delta,actor)!=1 || allocations.returned(row,target)!=1
                || allocations.journalReturn(row,target,delta,sku.getStock(),sku.getStock()+delta)!=1)
            throw exception0(409,"普通库存、原划拨归还进度与流水未完整保存");
        summarize(row,actor);
        row.setReturnedQuantity(target);row.setStatus(target==row.getAllocatedQuantity()?RETURNED:RETURNING);row.setVersion(row.getVersion()+1);
        searchChanges.mark(row.getTenantId());
        return result(row);
    }

    /** 只读已提交结果；null 只表示此刻未看到记录，不触发回补/重新发布。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5,readOnly=true)
    public ProductSeckillAllocationRespDTO get(ProductSeckillAllocationReqDTO body) {
        ProductSeckillAllocationDO row=original(body,false);
        return row==null ? null : result(row);
    }

    private ProductSeckillAllocationDO original(ProductSeckillAllocationReqDTO body,boolean write) {
        return original(body,write,write);
    }
    private ProductSeckillAllocationDO original(ProductSeckillAllocationReqDTO body,boolean write,boolean ensure) {
        if (body==null || !validator.validate(body).isEmpty()) throw exception0(400,"活动发布身份、数量或版本不正确");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore())
            throw exception0(403,"秒杀划拨需要可信的明确租户");
        ProductSeckillAllocationDO seed=BeanUtils.toBean(body,ProductSeckillAllocationDO.class);
        seed.setTenantId(tenant);
        String hash=DigestUtil.sha256Hex("seckill-allocation-v1|"+tenant+'|'+body.getActivityNo()+'|'+body.getGeneration()
                +'|'+body.getOperationNo()+'|'+body.getSpuId()+'|'+body.getSkuId()+'|'+body.getSpuVersion()+'|'+body.getSkuVersion()+'|'+body.getQuantity());
        seed.setPayloadHash(hash);
        if (ensure) allocations.ensure(seed);
        ProductSeckillAllocationDO row=write ? allocations.lock(tenant,body.getOperationNo()) : allocations.get(tenant,body.getOperationNo());
        if (row==null) {
            if (ensure) throw exception0(409,"本活动代次的规格已绑定其他划拨业务号");
            return null;
        }
        if (!hash.equals(row.getPayloadHash()) || !Objects.equals(row.getActivityNo(),body.getActivityNo())
                || !Objects.equals(row.getGeneration(),body.getGeneration()) || !Objects.equals(row.getSpuId(),body.getSpuId())
                || !Objects.equals(row.getSkuId(),body.getSkuId()) || !Objects.equals(row.getSpuVersion(),body.getSpuVersion())
                || !Objects.equals(row.getSkuVersion(),body.getSkuVersion()) || !Objects.equals(row.getQuantity(),body.getQuantity()))
            throw exception0(409,"同一发布业务号不能更换活动、规格、数量或版本");
        if (row.getStatus()==null || !Arrays.asList(PENDING,ALLOCATED,COMPENSATED,RETURNED,REVIEW,RETURNING).contains(row.getStatus())
                || row.getAllocatedQuantity()==null || row.getReturnedQuantity()==null || row.getVersion()==null
                || row.getVersion()<0 || row.getVersion()>=9_007_199_254_740_991L
                || (row.getAllocatedQuantity()!=0 && !row.getAllocatedQuantity().equals(row.getQuantity()))
                || row.getReturnedQuantity()<0 || row.getReturnedQuantity()>row.getAllocatedQuantity()
                || (row.getStatus()==PENDING && (row.getAllocatedQuantity()!=0 || row.getReturnedQuantity()!=0))
                || (row.getStatus()==ALLOCATED && (!row.getAllocatedQuantity().equals(row.getQuantity()) || row.getReturnedQuantity()!=0))
                || (row.getStatus()==COMPENSATED && !row.getReturnedQuantity().equals(row.getAllocatedQuantity()))
                || (row.getStatus()==RETURNED && (!row.getAllocatedQuantity().equals(row.getQuantity()) || !row.getReturnedQuantity().equals(row.getAllocatedQuantity())))
                || (row.getStatus()==RETURNING && (!row.getAllocatedQuantity().equals(row.getQuantity()) || row.getReturnedQuantity()<=0 || row.getReturnedQuantity()>=row.getAllocatedQuantity())))
            throw exception0(503,"原划拨凭证不完整，需核查后恢复");
        return row;
    }

    private void journal(ProductSeckillAllocationDO row,String type,Integer before,Integer after,String reason) {
        if (allocations.journal(row,row.getOperationNo()+":"+type,type,before,after,reason)!=1)
            throw new IllegalStateException("秒杀库存流水写入失败");
    }

    private void summarize(ProductSeckillAllocationDO row,String actor) {
        ProductSpuSkuSummary summary=skus.summarize(row.getTenantId(),row.getSpuId());
        if (summary==null || summary.getStock()==null || summary.getStock()<0 || summary.getStock()>Integer.MAX_VALUE
                || stocks.summarize(row.getTenantId(),row.getSpuId(),summary.getStock().intValue(),actor)!=1)
            throw exception0(409,"商品可售汇总超出上限或原商品缺失");
    }

    private String actor(ProductSeckillAllocationDO row) { return "seckill:"+row.getId(); }
    private ProductSeckillAllocationRespDTO result(ProductSeckillAllocationDO row) {
        return BeanUtils.toBean(row,ProductSeckillAllocationRespDTO.class);
    }
}
