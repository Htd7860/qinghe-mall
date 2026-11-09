package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.ZoneOffset;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用原资源恢复/结果查询入口；恢复命令重读原资金证明，结果查询只读本库。 */
@Service @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class SeckillRefundResourceService {
    private final SeckillAdmissionMapper requests;
    private final SeckillOrderResourceMapper resources;
    private final SeckillRefundRecordMapper refunds;
    private final ObjectProvider<SeckillRefundRecordService> records;
    private final ObjectProvider<SeckillRefundStockService> stocks;
    private final TransactionTemplate read;
    public SeckillRefundResourceService(SeckillAdmissionMapper requests,SeckillOrderResourceMapper resources,SeckillRefundRecordMapper refunds,
            ObjectProvider<SeckillRefundRecordService> records,ObjectProvider<SeckillRefundStockService> stocks,PlatformTransactionManager manager) {
        this.requests=requests;this.resources=resources;this.refunds=refunds;this.records=records;this.stocks=stocks;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public SeckillRefundResourceReceiptDTO recover(SeckillRefundResourceLookupDTO q) {
        SeckillRefundCommandContext.requireCaller();Long tenant=context(q);
        SeckillRequestDO r=read.execute(tx->original(tenant,q));
        SeckillRefundRecordService record=records.getIfAvailable();SeckillRefundStockService stock=stocks.getIfAvailable();
        if(record==null || stock==null)throw exception0(503,"秒杀退款恢复尚未启用");
        if(!"PAID".equals(r.getResourceState()))return get(q); // 原付款资源仍由既有订单终局任务核对。
        record.record(q.getRequestNo(),q.getAfterSaleId());
        stock.restore(q.getAfterSaleId());
        return get(q); // 数据库恢复成功不等于缓存成功，始终返回持久进度。
    }
    public SeckillRefundResourceReceiptDTO get(SeckillRefundResourceLookupDTO q) {
        Long tenant=context(q);
        return read.execute(tx->{
            SeckillRequestDO r=original(tenant,q);
            SeckillRefundRecordDO f=refunds.get(tenant,q.getAfterSaleId());
            if(f==null)return null;
            if(!Objects.equals(f.getRequestNo(),r.getRequestNo()) || !Objects.equals(f.getOrderId(),r.getOrderId())
                    || !Objects.equals(f.getMemberId(),r.getMemberId()) || !Objects.equals(f.getActivityId(),r.getActivityId())
                    || !Objects.equals(f.getGeneration(),r.getGeneration()) || !Objects.equals(f.getRedisEpoch(),r.getRedisEpoch())
                    || !Objects.equals(f.getSkuId(),r.getSkuId()) || f.getRefundedCount()>r.getQuantity()
                    || f.getRestoreCount()>f.getRefundedCount() || f.getRefundAmount()>r.getPayAmount()
                    || !"PAID".equals(r.getResourceState()) || resources.cacheProof(tenant,r.getRequestNo())==null)
                throw exception0(503,"原退款及付款资源归属不一致");
            if("RESTORED".equals(f.getState())) {
                f=refunds.restoredProof(tenant,q.getAfterSaleId());
                if(f==null || f.getRestoredTotal()>r.getQuantity())throw exception0(503,"原退款恢复流水未确认");
            }
            SeckillRefundResourceReceiptDTO out=new SeckillRefundResourceReceiptDTO();
            out.setTenantId(tenant);out.setRequestNo(r.getRequestNo());out.setRequestDigest(r.getRequestDigest());
            out.setMemberId(f.getMemberId());out.setOrderId(f.getOrderId());out.setAfterSaleId(f.getAfterSaleId());out.setOrderItemId(f.getOrderItemId());
            out.setRefundNo(f.getRefundNo());out.setRefundedCount(f.getRefundedCount());out.setRestoreCount(f.getRestoreCount());out.setRefundAmount(f.getRefundAmount());
            out.setPayRefundId(f.getPayRefundId());out.setRefundTime(f.getRefundTime().atOffset(ZoneOffset.UTC));
            out.setAfterSaleVersion(f.getAfterSaleVersion());out.setOrderVersion(f.getOrderVersion());out.setState(f.getState());out.setCacheState(f.getCacheState());
            out.setRestoredTotal(f.getRestoredTotal());out.setRestoredTime(f.getRestoredTime()==null?null:f.getRestoredTime().atOffset(ZoneOffset.UTC));return out;
        });
    }
    private SeckillRequestDO original(Long tenant,SeckillRefundResourceLookupDTO q) {
        SeckillRequestDO r=requests.forDispatch(tenant,q.getRequestNo());
        if(r==null || !Objects.equals(r.getMemberId(),q.getMemberId()) || !Objects.equals(r.getOrderId(),q.getOrderId())
                || !Objects.equals(r.getRequestDigest(),q.getRequestDigest()))throw exception0(409,"原退款申请尚未绑定该订单");
        return r;
    }
    private Long context(SeckillRefundResourceLookupDTO q) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("退款资源入口不能嵌套事务");
        Long tenant=TenantContextHolder.getTenantId();
        if(!id(tenant) || TenantContextHolder.isIgnore())throw exception0(403,"需要可信当前租户");
        if(q==null || !id(q.getMemberId()) || !id(q.getOrderId()) || !id(q.getAfterSaleId()) || q.getRequestNo()==null
                || !q.getRequestNo().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || q.getRequestDigest()==null || !q.getRequestDigest().matches("[0-9a-f]{64}"))throw exception0(400,"需要原订单申请及售后引用");
        return tenant;
    }
    private boolean id(Long value){return value!=null && value>0 && value<=9007199254740991L;}
}
