package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.trade.SeckillOrderResultClient;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillRefundSnapshotDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.ZoneOffset;
import java.util.Objects;

/** 沿用原售后按商品单位恢复库存的职责；专属池先记录原资金/实物义务，不走普通库存。 */
@Service
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillRefundRecordService {
    private final SeckillAdmissionMapper admissions;
    private final SeckillActivityMapper activities;
    private final SeckillOrderResultMapper requests;
    private final SeckillOrderResourceMapper resources;
    private final SeckillRefundRecordMapper refunds;
    private final SeckillOrderResultClient trade;
    private final TransactionTemplate write;

    public SeckillRefundRecordService(SeckillAdmissionMapper admissions,SeckillActivityMapper activities,
            SeckillOrderResultMapper requests,SeckillOrderResourceMapper resources,SeckillRefundRecordMapper refunds,
            SeckillOrderResultClient trade,PlatformTransactionManager manager) {
        this.admissions=admissions;this.activities=activities;this.requests=requests;this.resources=resources;
        this.refunds=refunds;this.trade=trade;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** 后台或精确服务命令调用；true仅表示原退款已登记，不表示库存或缓存已恢复。 */
    public boolean record(String requestNo,Long afterSaleId) {
        SeckillRefundCommandContext.requireCaller();
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || afterSaleId==null || afterSaleId<=0 || afterSaleId>9007199254740991L || requestNo==null
                || !requestNo.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("需要原租户申请号及售后号");
        SeckillRequestDO route=admissions.forDispatch(tenant,requestNo);
        if(route==null || route.getOrderId()==null)throw new IllegalStateException("原秒杀订单未绑定");
        SeckillRefundSnapshotDTO proof=trade.refund(route,afterSaleId); // 网络核验在本地事务和锁之外。
        return Boolean.TRUE.equals(write.execute(tx -> {
            if(!Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())
                throw new IllegalStateException("退款登记租户发生变化");
            // 只登记历史义务，不要求当前代次仍相同；旧代次退款也必须保留，后续按原代次归还。
            if(activities.lock(tenant,route.getActivityId())==null)throw new IllegalStateException("原活动缺失，请核查");
            SeckillRequestDO r=requests.lockRequest(tenant,requestNo);
            if(r==null || !Objects.equals(r.getOrderId(),route.getOrderId()) || !Objects.equals(r.getMemberId(),route.getMemberId())
                    || !Objects.equals(r.getActivityId(),route.getActivityId()) || !Objects.equals(r.getGeneration(),route.getGeneration())
                    || !Objects.equals(r.getRedisEpoch(),route.getRedisEpoch()) || !Objects.equals(r.getSkuId(),route.getSkuId())
                    || !Objects.equals(r.getRequestDigest(),route.getRequestDigest()) || !Objects.equals(r.getQuantity(),route.getQuantity())
                    || !Objects.equals(r.getPayAmount(),route.getPayAmount()) || !"PAID".equals(r.getResourceState())
                    || r.getResourceTime()==null || r.getResourceOrderVersion()==null || proof.getOrderVersion()<r.getResourceOrderVersion()
                    || proof.getRefundTime().toInstant().isBefore(r.getResourceTime().toInstant(ZoneOffset.UTC))
                    || resources.journalExists(r,"ORDER_PAID",true)!=1)
                throw new IllegalStateException("原付款资源尚未核对，请先恢复原付款账本");
            SeckillRefundRecordDO f=project(r,proof);
            SeckillRefundRecordDO old=refunds.get(tenant,afterSaleId);
            if(old!=null) {
                if(!sameRefund(old,f))throw new IllegalStateException("原售后号退款事实冲突，请核查原账");
                return true; // 原号重试不新增义务、不复位后续回补状态。
            }
            if(refunds.withinOriginal(r,f)!=1)throw new IllegalStateException("累计退款数量或金额超过原秒杀订单");
            if(refunds.insert(f)!=1)throw new IllegalStateException("原退款库存义务未完整登记");
            return true;
        }));
    }

    private SeckillRefundRecordDO project(SeckillRequestDO r,SeckillRefundSnapshotDTO proof) {
        SeckillRefundRecordDO f=new SeckillRefundRecordDO();
        f.setTenantId(r.getTenantId());f.setAfterSaleId(proof.getAfterSaleId());f.setRequestNo(r.getRequestNo());
        f.setOrderId(r.getOrderId());f.setOrderItemId(proof.getOrderItemId());f.setMemberId(r.getMemberId());
        f.setActivityId(r.getActivityId());f.setGeneration(r.getGeneration());f.setRedisEpoch(r.getRedisEpoch());f.setSkuId(r.getSkuId());
        f.setRefundNo(proof.getRefundNo());f.setRefundedCount(proof.getRefundedCount());f.setRestoreCount(proof.getRestoreCount());
        f.setRefundAmount(proof.getRefundAmount());f.setPayRefundId(proof.getPayRefundId());
        f.setRefundTime(java.time.LocalDateTime.ofInstant(proof.getRefundTime().toInstant(),ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        f.setAfterSaleVersion(proof.getAfterSaleVersion());f.setOrderVersion(proof.getOrderVersion());f.setShipped(proof.getShipped());
        f.setState(proof.getRestoreCount()==0?"NOT_REQUIRED":"PENDING");return f;
    }

    private boolean sameRefund(SeckillRefundRecordDO old,SeckillRefundRecordDO f) {
        return Objects.equals(old.getTenantId(),f.getTenantId()) && Objects.equals(old.getAfterSaleId(),f.getAfterSaleId())
                && Objects.equals(old.getRequestNo(),f.getRequestNo()) && Objects.equals(old.getOrderId(),f.getOrderId())
                && Objects.equals(old.getOrderItemId(),f.getOrderItemId()) && Objects.equals(old.getMemberId(),f.getMemberId())
                && Objects.equals(old.getActivityId(),f.getActivityId()) && Objects.equals(old.getGeneration(),f.getGeneration())
                && Objects.equals(old.getRedisEpoch(),f.getRedisEpoch()) && Objects.equals(old.getSkuId(),f.getSkuId())
                && Objects.equals(old.getRefundNo(),f.getRefundNo()) && Objects.equals(old.getRefundedCount(),f.getRefundedCount())
                && Objects.equals(old.getRestoreCount(),f.getRestoreCount()) && Objects.equals(old.getRefundAmount(),f.getRefundAmount())
                && Objects.equals(old.getPayRefundId(),f.getPayRefundId()) && Objects.equals(old.getRefundTime(),f.getRefundTime())
                && Objects.equals(old.getShipped(),f.getShipped()) && f.getOrderVersion()>=old.getOrderVersion()
                && f.getAfterSaleVersion()>=old.getAfterSaleVersion();
    }
}
