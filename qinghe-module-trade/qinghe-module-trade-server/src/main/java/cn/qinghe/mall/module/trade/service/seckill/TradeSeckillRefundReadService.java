package cn.qinghe.mall.module.trade.service.seckill;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.TradeSeckillOrderBindingMapper;
import cn.qinghe.mall.module.trade.service.aftersale.AfterSaleRefundCalculator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.ZoneId;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原退款结果读取：一份RR快照核对订单、退款、售后和原REFUNDED单位，不调用渠道或库存。 */
@Service @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class TradeSeckillRefundReadService {
    private final TradeOrderMapper orders;
    private final TradeOrderPrepareMapper preparations;
    private final TradeSeckillOrderBindingMapper bindings;
    private final AfterSaleRefundMapper refunds;
    private final AfterSaleDecisionMapper sales;
    private final TransactionTemplate read;
    public TradeSeckillRefundReadService(TradeOrderMapper orders,TradeOrderPrepareMapper preparations,TradeSeckillOrderBindingMapper bindings,
            AfterSaleRefundMapper refunds,AfterSaleDecisionMapper sales,PlatformTransactionManager manager) {
        this.orders=orders;this.preparations=preparations;this.bindings=bindings;this.refunds=refunds;this.sales=sales;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public SeckillRefundSnapshotDTO get(SeckillRefundLookupDTO q) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("退款事实不能嵌套外层事务");
        Long tenant=TenantContextHolder.getTenantId();
        if(!id(tenant) || TenantContextHolder.isIgnore())throw exception0(403,"需要可信当前租户");
        if(q==null || !id(q.getOrderId()) || !id(q.getAfterSaleId()))throw exception0(400,"需要原订单及售后号");
        return read.execute(tx -> {
            AfterSaleRefundDO f=refunds.get(tenant,q.getAfterSaleId());AfterSaleDO sale=sales.get(tenant,q.getAfterSaleId());
            if(f==null || sale==null || !Objects.equals(f.getOrderId(),q.getOrderId()) || !Objects.equals(sale.getOrderId(),q.getOrderId()))throw incomplete();
            TradeOrderPrepareDO prepared=preparations.get(tenant,q.getOrderId());
            if(prepared==null || !Integer.valueOf(1).equals(prepared.getSourceType()))throw incomplete();
            cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillOrderBindingDO binding=bindings.get(tenant,prepared.getRequestId());
            TradeOrderDO o=TradeSeckillResultReadService.validatedOrder(orders,preparations,tenant,binding);
            TradeOrderItemDO item=refunds.originalItem(tenant,q.getOrderId(),f.getOrderItemId());
            if(!Objects.equals(o.getId(),q.getOrderId()) || !Boolean.TRUE.equals(o.getPayStatus()) || !Objects.equals(o.getPayOrderId(),f.getPayOrderId())
                    || !Objects.equals(o.getUserId(),f.getUserId()) || !Objects.equals(sale.getUserId(),f.getUserId())
                    || !Objects.equals(sale.getOrderItemId(),f.getOrderItemId()) || item==null || !Integer.valueOf(1).equals(item.getSourceType())
                    || !Objects.equals(item.getUserId(),o.getUserId()) || !Objects.equals(item.getSkuId(),binding.getSkuId())
                    || !"SUCCESS".equals(f.getStatus()) || !Integer.valueOf(50).equals(sale.getStatus()) || f.getExecutionRequestedAt()==null
                    || f.getRefundTime()==null || !Objects.equals(f.getRefundTime(),sale.getRefundTime())
                    || o.getPayTime()==null || f.getRefundTime().isBefore(o.getPayTime())
                    || f.getTotalPrice()==null || f.getTotalPrice()<0 || f.getTotalPrice()>o.getPayPrice() || o.getRefundPrice()<f.getTotalPrice()
                    || !Objects.equals(f.getTotalPrice(),Math.addExact(f.getGoodsPrice(),f.getDeliveryPrice()))
                    || !Objects.equals(f.getGoodsPrice(),sale.getGoodsRefundPrice()) || f.getRefundNo()==null || f.getRefundNo().length()>64
                    || sale.getCount()==null || sale.getCount()<1 || sale.getCount()>item.getCount() || item.getRefundCount()<sale.getCount()
                    || f.getRestoreCount()==null || f.getRestoreCount()<0 || f.getRestoreCount()>sale.getCount()
                    || f.getTotalPrice()>0 && (!id(f.getPayRefundId()) || f.getPayRefundVersion()==null)
                    || f.getTotalPrice()==0 && f.getPayRefundId()!=null)throw incomplete();
            if(f.getShipped()==null || !Arrays.asList(10,20).contains(sale.getWay()))throw incomplete();
            boolean shipped=f.getShipped(); // 原冻结事实；部分退款后剩余商品可能后来才发货。
            if(shipped && (o.getDeliveryTime()==null || o.getDeliveryTime().isAfter(f.getRefundTime()))
                    || !shipped && o.getDeliveryTime()!=null && o.getDeliveryTime().isBefore(f.getRefundTime()))throw incomplete();
            if(Integer.valueOf(20).equals(sale.getWay()) && (sale.getReceiveTime()==null || !Objects.equals(sale.getReceivedCount(),sale.getCount())
                    || sale.getSalableCount()==null || sale.getSalableCount()<0 || sale.getSalableCount()>sale.getCount()))throw incomplete();
            int restore=shipped?Integer.valueOf(20).equals(sale.getWay())?sale.getSalableCount():0:sale.getCount();
            if(f.getRestoreCount()!=restore)throw incomplete();
            List<AfterSaleUnitDO> units=sales.refundedUnits(tenant,q.getAfterSaleId());
            if(units==null || units.size()!=sale.getCount())throw incomplete();
            Set<Integer> numbers=new HashSet<>();long goods=0;
            for(AfterSaleUnitDO unit:units) {
                if(unit==null || !Objects.equals(unit.getTenantId(),tenant) || !Objects.equals(unit.getAfterSaleId(),sale.getId())
                        || !Objects.equals(unit.getOrderId(),o.getId()) || !Objects.equals(unit.getOrderItemId(),item.getId())
                        || !"REFUNDED".equals(unit.getState()) || !numbers.add(unit.getUnitNo()))throw incomplete();
                goods=Math.addExact(goods,unit.getGoodsPrice());
            }
            AfterSaleRefundCalculator.verifyOriginalUnits(item,units);
            if(goods!=f.getGoodsPrice())throw incomplete();
            SeckillRefundSnapshotDTO out=new SeckillRefundSnapshotDTO();out.setSchemaVersion(1);out.setBinding(TradeSeckillQuoteBindingService.project(binding));
            out.setAfterSaleId(sale.getId());out.setOrderItemId(item.getId());out.setRefundNo(f.getRefundNo());out.setRefundedCount(sale.getCount());
            out.setRestoreCount(f.getRestoreCount());out.setRefundAmount(f.getTotalPrice());out.setPayRefundId(f.getPayRefundId());
            out.setRefundTime(f.getRefundTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
            out.setAfterSaleVersion(sale.getAuthVersion());out.setOrderVersion(o.getAuthVersion());out.setShipped(shipped);return out;
        });
    }
    private boolean id(Long x){return x!=null && x>0 && x<=9007199254740991L;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"原秒杀退款、订单或实物回补事实需核对");}
}
