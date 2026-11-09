package cn.qinghe.mall.module.trade.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillEventDTO;
import cn.qinghe.mall.module.trade.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用原事件RR读取方式：同一快照核对结果Outbox、永久绑定和实际订单主从。 */
@Service @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class TradeSeckillResultReadService {
    private final TradeSeckillResultPublishMapper events;
    private final TradeSeckillOrderBindingMapper bindings;
    private final TradeOrderMapper orders;
    private final TradeOrderPrepareMapper preparations;
    private final TransactionTemplate read;
    public TradeSeckillResultReadService(TradeSeckillResultPublishMapper events,TradeSeckillOrderBindingMapper bindings,
            TradeOrderMapper orders,TradeOrderPrepareMapper preparations,PlatformTransactionManager manager) {
        this.events=events;this.bindings=bindings;this.orders=orders;this.preparations=preparations;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public SeckillOrderResultSnapshotDTO get(SeckillOrderResultLookupDTO query) {
        Long tenant=context();
        if (query==null || query.getEventId()==null || !query.getEventId().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw exception0(400,"需要原结果事件号");
        return read.execute(tx -> {
            TradeSeckillOutboxDO original=events.original(tenant,query.getEventId());
            if (original==null) throw exception0(404,"原秒杀建单结果不存在");
            SeckillEventDTO event=frame(original,tenant);
            TradeSeckillOrderBindingDO b=bindings.get(tenant,event.getRequestNo());
            int expected="SECKILL_ORDER_CREATED".equals(event.getEventType())?10:20;
            if (b==null || !Integer.valueOf(expected).equals(b.getState()) || !Objects.equals(b.getFenceVersion(),event.getAggregateVersion())) throw incomplete();
            SeckillOrderResultSnapshotDTO out=new SeckillOrderResultSnapshotDTO();
            out.setTenantId(tenant);out.setEventId(event.getEventId());out.setRequestNo(event.getRequestNo());out.setEventType(event.getEventType());
            out.setAggregateVersion(event.getAggregateVersion());out.setSchemaVersion(1);out.setOccurredAt(event.getOccurredAt());
            out.setBinding(TradeSeckillQuoteBindingService.project(b));
            if (expected==10) {
                TradeOrderDO o=order(tenant,b);
                out.setOrderCreatedAt(o.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
                out.setPayDeadline(o.getPayDeadline().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
            } else if (b.getOrderId()!=null || b.getClosedAt()==null || b.getFenceVersion()<1) throw incomplete();
            return out;
        });
    }
    /** 原六项引用定位，无调用方提供的“已付/已取消”结论；与订单主从/准备记录同一个RR快照。 */
    public SeckillOrderStateDTO state(SeckillQuoteBindingReqDTO query) {
        Long tenant=context();
        if (query==null || query.getMemberId()==null || query.getMemberId()<=0 || query.getMemberId()>9007199254740991L
                || !uuid(query.getRequestNo()) || !uuid(query.getClientRequestId()) || !uuid(query.getQuoteId())
                || !digest(query.getRequestDigest()) || !digest(query.getQuoteDigest())) throw exception0(400,"需要原秒杀订单引用");
        return read.execute(tx -> {
            TradeSeckillOrderBindingDO b=bindings.get(tenant,query.getRequestNo());
            if (b==null) throw exception0(404,"原秒杀绑定尚未观察到");
            if (!Objects.equals(b.getMemberId(),query.getMemberId()) || !Objects.equals(b.getClientRequestId(),query.getClientRequestId())
                    || !Objects.equals(b.getRequestDigest(),query.getRequestDigest()) || !Objects.equals(b.getQuoteId(),query.getQuoteId())
                    || !Objects.equals(b.getQuoteDigest(),query.getQuoteDigest())) throw exception0(409,"原秒杀订单引用不一致");
            if (!Integer.valueOf(10).equals(b.getState()) || b.getOrderId()==null || b.getClosedAt()!=null)
                throw exception0(409,"原申请尚无已建订单状态证明");
            TradeOrderDO o=order(tenant,b);financial(o);
            SeckillOrderStateDTO out=new SeckillOrderStateDTO();out.setBinding(TradeSeckillQuoteBindingService.project(b));
            out.setOrderVersion(o.getAuthVersion());out.setOrderStatus(o.getStatus());out.setPayStatus(o.getPayStatus());
            out.setPayOrderId(o.getPayOrderId());out.setOrderCreatedAt(offset(o.getCreateTime()));out.setPayDeadline(offset(o.getPayDeadline()));
            out.setPayTime(offset(o.getPayTime()));out.setCancelTime(offset(o.getCancelTime()));out.setCancelType(o.getCancelType());
            out.setCancelResourcesReleased(o.getCancelResourcesReleased());out.setRefundPrice(o.getRefundPrice());return out;
        });
    }
    /** 直接复用原result-event的订单/准备/单商品事实校验，不另做一套建单来源。 */
    private TradeOrderDO order(Long tenant,TradeSeckillOrderBindingDO b) {
        return validatedOrder(orders,preparations,tenant,b);
    }
    /** 支付后台复用相同事实校验；调用方必须在自己的只读快照中调用，不获取或更改身份。 */
    public static TradeOrderDO validatedOrder(TradeOrderMapper orders,TradeOrderPrepareMapper preparations,Long tenant,TradeSeckillOrderBindingDO b) {
        if (b==null || !Objects.equals(tenant,b.getTenantId()) || !Integer.valueOf(10).equals(b.getState())
                || b.getOrderId()==null || b.getClosedAt()!=null) throw incomplete();
        TradeOrderDO o=orders.owned(tenant,b.getMemberId(),b.getOrderId());
        TradeOrderPrepareDO p=preparations.get(tenant,b.getOrderId());
        List<TradeOrderItemDO> items=orders.memberItems(tenant,b.getMemberId(),Collections.singletonList(b.getOrderId()));
        if (o==null || p==null || !Integer.valueOf(1).equals(o.getType()) || !Integer.valueOf(1).equals(p.getSourceType())
                || !"CREATED".equals(p.getStage()) || !Objects.equals(p.getRequestId(),b.getRequestNo())
                || !Objects.equals(p.getRequestHash(),b.getRequestDigest()) || !Objects.equals(p.getQuoteId(),b.getQuoteId())
                || !Objects.equals(p.getUserId(),b.getMemberId()) || !Objects.equals(o.getProductCount(),b.getQuantity())
                || !Objects.equals(o.getTotalPrice(),b.getGoodsAmount()) || !Objects.equals(o.getDeliveryPrice(),b.getShippingAmount())
                || !Objects.equals(o.getPayPrice(),b.getPayAmount()) || !Long.valueOf(0).equals(o.getDiscountPrice()) || o.getCouponId()!=null
                || o.getCreateTime()==null || o.getPayDeadline()==null || !o.getPayDeadline().equals(o.getCreateTime().plusMinutes(30))
                || items==null || items.size()!=1) throw incomplete();
        TradeOrderItemDO i=items.get(0);
        if (!Integer.valueOf(1).equals(i.getSourceType()) || i.getCartId()!=null || !Objects.equals(i.getSkuId(),b.getSkuId())
                || !Objects.equals(i.getCount(),b.getQuantity()) || !Objects.equals(i.getPrice(),b.getPriceCent())
                || !Objects.equals(i.getGoodsPrice(),b.getGoodsAmount()) || !Objects.equals(i.getDeliveryPrice(),b.getShippingAmount())
                || !Objects.equals(i.getPayPrice(),b.getPayAmount()) || !Long.valueOf(0).equals(i.getDiscountPrice())) throw incomplete();
        return o;
    }
    private void financial(TradeOrderDO o) {
        if (o.getAuthVersion()==null || o.getAuthVersion()<0 || o.getAuthVersion()>9007199254740991L
                || !Arrays.asList(0,10,20,30,40).contains(o.getStatus()) || o.getPayStatus()==null || o.getCancelResourcesReleased()==null
                || o.getRefundPrice()==null || o.getRefundPrice()<0 || o.getRefundPrice()>o.getPayPrice()
                || o.getPayOrderId()!=null && (o.getPayOrderId()<=0 || o.getPayOrderId()>9007199254740991L)) throw incomplete();
        if (Boolean.TRUE.equals(o.getPayStatus())) {
            if (o.getPayOrderId()==null || o.getPayTime()==null || o.getPayTime().isBefore(o.getCreateTime().minusSeconds(1))
                    || !o.getPayTime().isBefore(o.getPayDeadline()) || Integer.valueOf(0).equals(o.getStatus())) throw incomplete();
        } else if (o.getPayTime()!=null || o.getRefundPrice()!=0 || !Arrays.asList(0,40).contains(o.getStatus())
                || Integer.valueOf(40).equals(o.getStatus()) && (o.getCancelTime()==null || !Arrays.asList(10,30).contains(o.getCancelType())
                    || o.getPayOrderId()==null)) throw incomplete();
        if (Boolean.TRUE.equals(o.getCancelResourcesReleased()) && !Integer.valueOf(40).equals(o.getStatus())) throw incomplete();
    }
    private Long context() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("原结果读取不能嵌套外层事务");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()) throw exception0(403,"需要可信当前租户");return tenant;
    }
    private static OffsetDateTime offset(LocalDateTime time) { return time==null?null:time.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(); }
    private static boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private static boolean digest(String x) { return x!=null && x.matches("[0-9a-f]{64}"); }
    /** 固定引用信封；消息只放原事件，不把订单事实或地址塞进MQ。 */
    public static SeckillEventDTO frame(TradeSeckillOutboxDO row,Long tenant) {
        if (row==null || !Objects.equals(row.getTenantId(),tenant) || row.getCreatedAt()==null || row.getPayload()==null || row.getPayload().length()>2048
                || !Arrays.asList("SECKILL_ORDER_CREATED","SECKILL_REQUEST_CLOSED").contains(row.getEventType())) throw new IllegalStateException("原结果事件不完整");
        Map<?,?> p=JsonUtils.parseObject(row.getPayload(),Map.class);
        if (p==null || p.size()!=4 || !Integer.valueOf(1).equals(row.getSchemaVersion()) || !(p.get("schemaVersion") instanceof Number)
                || !"1".equals(p.get("schemaVersion").toString()) || !(p.get("tenantId") instanceof Number)
                || !tenant.toString().equals(p.get("tenantId").toString()) || !Objects.equals(p.get("requestNo"),row.getRequestNo())
                || !(p.get("aggregateVersion") instanceof Number) || row.getAggregateVersion()==null
                || !row.getAggregateVersion().toString().equals(p.get("aggregateVersion").toString())) throw new IllegalStateException("原结果事件引用不一致");
        SeckillEventDTO e=new SeckillEventDTO();e.setTenantId(tenant);e.setEventId(row.getEventId());e.setRequestNo(row.getRequestNo());
        e.setEventType(row.getEventType());e.setAggregateVersion(row.getAggregateVersion());e.setSchemaVersion(1);
        e.setOccurredAt(row.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());SeckillEventEnvelope.encode(e);return e;
    }
    private static cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原结果事件与实际建单事实不一致，请核查原申请"); }
}
