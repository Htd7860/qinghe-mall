package cn.qinghe.mall.module.trade.service.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.*;
import cn.qinghe.mall.module.trade.framework.seckill.SeckillDispatchClient;
import cn.qinghe.mall.module.trade.service.message.TradeOrderEventEnvelope;
import cn.qinghe.mall.module.trade.service.seckill.dto.TradeSeckillQuoteSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原createOrder的主从保存职责，复用现有准备表ID、OrderMapper与到期事件。
 * 本地事务不扣普通库存/券、不发消息。由独立命令消费者委托；支付/取消联动完成前保持关闭。
 * @author 芋道源码
 */
@Service
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
public class TradeSeckillOrderCreationService {
    private final SeckillDispatchClient source;
    private final CartMapper members;
    private final TradeSeckillOrderBindingMapper bindings;
    private final TradeSeckillQuoteMapper quotes;
    private final TradeSeckillOrderMapper ledger;
    private final TradeSeckillSnapshotReader snapshots;
    private final TradeOrderPrepareMapper preparations;
    private final TradeOrderMapper orders;
    private final TradeOrderOutboxMapper events;
    private final TransactionTemplate write;
    public TradeSeckillOrderCreationService(SeckillDispatchClient source,CartMapper members,TradeSeckillOrderBindingMapper bindings,
            TradeSeckillQuoteMapper quotes,TradeSeckillOrderMapper ledger,TradeSeckillSnapshotReader snapshots,
            TradeOrderPrepareMapper preparations,TradeOrderMapper orders,TradeOrderOutboxMapper events,PlatformTransactionManager manager) {
        this.source=source;this.members=members;this.bindings=bindings;this.quotes=quotes;this.ledger=ledger;
        this.snapshots=snapshots;this.preparations=preparations;this.orders=orders;this.events=events;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    /** 只能由可信后台传入规范消息；来源RPC在事务外，只有已提交回执才能跳过RPC。 */
    public TradeSeckillInboxDO process(String body) {
        SeckillEventDTO event=SeckillEventEnvelope.decode(body);Long tenant=context(event);
        String hash=DigestUtil.sha256Hex(body);
        TradeSeckillInboxDO completed=ledger.receipt(tenant,event.getEventId());
        if (completed!=null) return sameReceipt(completed,event,hash);
        SeckillDispatchSnapshotDTO verified=source.verify(body);
        TradeSeckillInboxDO result=write.execute(tx -> apply(verified,hash));
        if (result==null) throw new IllegalStateException("秒杀建单事务没有返回消费回执");
        return result;
    }

    private TradeSeckillInboxDO apply(SeckillDispatchSnapshotDTO verified,String hash) {
        SeckillEventDTO e=verified.getEvent();SeckillAcceptedRequestDTO r=verified.getRequest();Long tenant=e.getTenantId();
        if (!Objects.equals(TenantContextHolder.getTenantId(),tenant) || TenantContextHolder.isIgnore()) throw exception0(403,"建单租户上下文已变化");
        members.ensureUser(tenant,r.getMemberId());
        if (members.lockVersion(tenant,r.getMemberId())==null) throw new IllegalStateException("会员建单父锁缺失");
        TradeSeckillInboxDO prior=ledger.receipt(tenant,e.getEventId());
        if (prior!=null) return sameReceipt(prior,e,hash);
        TradeSeckillOrderBindingDO binding=bindings.lock(tenant,r.getRequestNo());
        sameBinding(binding,r);
        if (Integer.valueOf(10).equals(binding.getState())) {
            TradeOrderDO order=orders.owned(tenant,r.getMemberId(),binding.getOrderId());
            if (order==null || !Integer.valueOf(1).equals(order.getType()) || !Objects.equals(order.getPayPrice(),binding.getPayAmount())
                    || r.getOrderId()!=null && !Objects.equals(r.getOrderId(),order.getId())) throw incomplete();
            return complete(e,hash,binding);
        }
        if (Integer.valueOf(20).equals(binding.getState())) {
            if (r.getOrderId()!=null) throw incomplete();
            return complete(e,hash,binding);
        }
        if (!Integer.valueOf(0).equals(binding.getState()) || r.getOrderId()!=null
                || Integer.valueOf(20).equals(r.getStatus()) || Integer.valueOf(60).equals(r.getStatus())) throw incomplete();
        TradeSeckillQuoteDO quote=quotes.lock(tenant,r.getMemberId(),r.getQuoteId());
        sameQuote(quote,r);
        LocalDateTime now=quotes.now();
        if (r.getAcceptedAt().toInstant().isAfter(now.toInstant(ZoneOffset.UTC))) throw incomplete();
        if ("SECKILL_CLOSE".equals(e.getEventType()) || !Integer.valueOf(10).equals(r.getStatus())
                || !now.isBefore(utc(r.getReconcileDeadline())) || !now.isBefore(utc(r.getPayDeadline()))) {
            if (bindings.closeBeforeOrder(tenant,r.getRequestNo())!=1
                    || quotes.closeSnapshot(tenant,r.getMemberId(),r.getQuoteId(),r.getQuoteDigest(),r.getRequestNo())!=1)
                throw new IllegalStateException("原建单栅栏关闭失败");
        } else {
            TradeSeckillQuoteSnapshot snapshot=snapshots.openVerified(quote);
            TradeOrderPrepareDO prepared=preparation(r,now);
            if (ledger.reserveOrderId(prepared)!=1 || !id(prepared.getId())) throw new IllegalStateException("共享订单编号分配失败");
            TradeOrderPrepareItemDO item=item(prepared,snapshot,r);
            if (preparations.insertItem(item)!=1 || !id(item.getId())) throw new IllegalStateException("共享商品项编号分配失败");
            TradeOrderDO order=order(prepared,snapshot,r,now);
            if (orders.insert(order)!=1 || orders.insertItems(tenant,r.getMemberId(),order.getId())!=1)
                throw new IllegalStateException("秒杀订单及商品项保存失败");
            // CREATE归属原受理会员；执行器不是新的用户请求，创建时刻沿用原受理时刻。
            if (orders.logCreated(tenant,r.getMemberId(),order.getId(),order.getCreateTime())!=1)
                throw new IllegalStateException("原会员建单记录保存失败");
            String expiry=JsonUtils.toJsonString(Collections.singletonMap("dueAt",TradeOrderEventEnvelope.epochCeiling(order.getPayDeadline())));
            if (events.insertExpiry(tenant,order.getId(),UUID.randomUUID().toString(),expiry,order.getPayDeadline(),order.getCreateTime())!=1)
                throw new IllegalStateException("秒杀订单到期事件保存失败");
            // 最后一次DB时钟期限裁决；失败回滚上面全部订单、编号记录、日志和事件。
            if (ledger.created(tenant,r.getRequestNo(),order.getId(),binding.getFenceVersion(),utc(r.getReconcileDeadline()))!=1
                    || ledger.attachQuote(tenant,r.getMemberId(),r.getQuoteId(),r.getRequestNo(),order.getId())!=1)
                throw exception0(409,"原建单期限或绑定状态已变化，请按原事件恢复");
        }
        return complete(e,hash,bindings.lock(tenant,r.getRequestNo()));
    }

    private TradeSeckillInboxDO complete(SeckillEventDTO e,String hash,TradeSeckillOrderBindingDO b) {
        if (b==null || !Arrays.asList(10,20).contains(b.getState())) throw incomplete();
        String type=b.getState()==10?"SECKILL_ORDER_CREATED":"SECKILL_REQUEST_CLOSED";
        String result=ledger.resultEvent(b.getTenantId(),b.getRequestNo(),b.getFenceVersion(),type);
        if (result==null) {
            result=UUID.randomUUID().toString();Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("schemaVersion",1);payload.put("tenantId",b.getTenantId());payload.put("requestNo",b.getRequestNo());payload.put("aggregateVersion",b.getFenceVersion());
            if (ledger.insertResult(b.getTenantId(),result,b.getRequestNo(),type,b.getFenceVersion(),JsonUtils.toJsonString(payload))!=1)
                throw new IllegalStateException("秒杀结果事件保存失败");
        }
        TradeSeckillInboxDO receipt=new TradeSeckillInboxDO();receipt.setTenantId(e.getTenantId());receipt.setEventId(e.getEventId());
        receipt.setRequestNo(e.getRequestNo());receipt.setAggregateVersion(e.getAggregateVersion());receipt.setEventType(e.getEventType());
        receipt.setPayloadHash(hash);receipt.setResultState(b.getState());receipt.setOrderId(b.getOrderId());receipt.setResultEventId(result);
        if (ledger.complete(receipt)!=1) throw new IllegalStateException("秒杀消费回执保存失败");
        return ledger.receipt(e.getTenantId(),e.getEventId());
    }

    private TradeOrderPrepareDO preparation(SeckillAcceptedRequestDTO r,LocalDateTime now) {
        TradeOrderPrepareDO p=new TradeOrderPrepareDO();p.setSourceType(1);p.setTenantId(r.getTenantId());p.setUserId(r.getMemberId());
        p.setRequestId(r.getRequestNo());p.setRequestHash(r.getRequestDigest());p.setQuoteId(r.getQuoteId());
        p.setNo("QH"+UUID.randomUUID().toString().replace("-",""));p.setStage("CREATED");p.setAuthVersion(0L);p.setRemark("");
        Map<String,Object> refs=new LinkedHashMap<>();refs.put("schemaVersion",1);refs.put("requestNo",r.getRequestNo());
        refs.put("quoteId",r.getQuoteId());refs.put("quoteDigest",r.getQuoteDigest());p.setSnapshot(JsonUtils.toJsonString(refs));
        p.setProductCount(r.getQuantity());p.setTotalPrice(r.getGoodsAmount());p.setDiscountPrice(0L);p.setDeliveryPrice(r.getShippingAmount());p.setPayPrice(r.getPayAmount());
        p.setCreatedAt(shanghai(r.getAcceptedAt()));p.setUpdatedAt(shanghai(now.atOffset(ZoneOffset.UTC)));p.setPrepareDeadline(shanghai(r.getReconcileDeadline()));return p;
    }
    private TradeOrderPrepareItemDO item(TradeOrderPrepareDO p,TradeSeckillQuoteSnapshot s,SeckillAcceptedRequestDTO r) {
        TradeOrderPrepareItemDO i=new TradeOrderPrepareItemDO();i.setTenantId(p.getTenantId());i.setPreparationId(p.getId());i.setSourceType(1);
        i.setSpuId(s.getProduct().getSpuId());i.setSpuName(s.getProduct().getName());i.setSkuId(r.getSkuId());
        i.setSpuVersion(s.getProduct().getSpuVersion());i.setSkuVersion(s.getProduct().getSkuVersion());
        i.setProperties(JsonUtils.toJsonString(s.getProduct().getProperties()));i.setPicUrl(s.getProduct().getPicUrl());
        i.setCount(r.getQuantity());i.setPrice(r.getPriceCent());i.setGoodsPrice(r.getGoodsAmount());i.setDiscountPrice(0L);
        i.setDeliveryPrice(r.getShippingAmount());i.setPayPrice(r.getPayAmount());i.setUnitDiscountBase(0L);i.setUnitDiscountRemainder(0);
        i.setUnitDeliveryBase(r.getShippingAmount()/r.getQuantity());i.setUnitDeliveryRemainder((int)(r.getShippingAmount()%r.getQuantity()));return i;
    }
    private TradeOrderDO order(TradeOrderPrepareDO p,TradeSeckillQuoteSnapshot s,SeckillAcceptedRequestDTO r,LocalDateTime now) {
        TradeOrderDO o=new TradeOrderDO();o.setId(p.getId());o.setTenantId(p.getTenantId());o.setNo(p.getNo());o.setType(1);o.setUserId(p.getUserId());
        o.setUserRemark("");o.setStatus(0);o.setAuthVersion(0L);o.setProductCount(p.getProductCount());o.setTotalPrice(p.getTotalPrice());
        o.setDiscountPrice(0L);o.setDeliveryPrice(p.getDeliveryPrice());o.setPayPrice(p.getPayPrice());o.setPayStatus(false);o.setRefundPrice(0L);
        o.setReceiverName(s.getAddress().getName());o.setReceiverMobile(s.getAddress().getMobile());o.setReceiverAreaId(s.getAddress().getAreaId());
        o.setReceiverDetailAddress(s.getAddress().getDetailAddress());o.setCreateTime(p.getCreatedAt());
        o.setUpdateTime(shanghai(now.atOffset(ZoneOffset.UTC)));o.setPayDeadline(shanghai(r.getPayDeadline()));return o;
    }

    private void sameBinding(TradeSeckillOrderBindingDO b,SeckillAcceptedRequestDTO r) {
        if (b==null || !Objects.equals(b.getTenantId(),r.getTenantId()) || !Objects.equals(b.getMemberId(),r.getMemberId())
                || !Objects.equals(b.getRequestNo(),r.getRequestNo()) || !Objects.equals(b.getClientRequestId(),r.getClientRequestId())
                || !Objects.equals(b.getRequestDigest(),r.getRequestDigest()) || !Objects.equals(b.getQuoteId(),r.getQuoteId())
                || !Objects.equals(b.getQuoteDigest(),r.getQuoteDigest())) throw incomplete();
        // 关闭先到的墓碑可以没有商品事实；其原身份引用仍必须完全相同。
        if (b.getActivityId()==null) { if (!Integer.valueOf(20).equals(b.getState())) throw incomplete();return; }
        if (!Objects.equals(b.getActivityId(),r.getActivityId()) || !Objects.equals(b.getGeneration(),r.getGeneration())
                || !Objects.equals(b.getActivityVersion(),r.getActivityVersion()) || !Objects.equals(b.getRedisEpoch(),r.getRedisEpoch())
                || !Objects.equals(b.getSkuId(),r.getSkuId()) || !Objects.equals(b.getQuantity(),r.getQuantity())
                || !Objects.equals(b.getPriceCent(),r.getPriceCent()) || !Objects.equals(b.getGoodsAmount(),r.getGoodsAmount())
                || !Objects.equals(b.getShippingAmount(),r.getShippingAmount()) || !Objects.equals(b.getPayAmount(),r.getPayAmount())
                || !Objects.equals(b.getQuoteExpiresAt(),utc(r.getQuoteExpiresAt()))) throw incomplete();
    }
    private void sameQuote(TradeSeckillQuoteDO q,SeckillAcceptedRequestDTO r) {
        if (q==null || !Objects.equals(q.getTenantId(),r.getTenantId()) || !Objects.equals(q.getMemberId(),r.getMemberId())
                || !Objects.equals(q.getQuoteId(),r.getQuoteId()) || !Objects.equals(q.getQuoteDigest(),r.getQuoteDigest())
                || !Objects.equals(q.getBoundRequestNo(),r.getRequestNo()) || q.getBoundOrderId()!=null || q.getSnapshotCipher()==null
                || !Objects.equals(q.getActivityId(),r.getActivityId()) || !Objects.equals(q.getGeneration(),r.getGeneration())
                || !Objects.equals(q.getActivityVersion(),r.getActivityVersion()) || !Objects.equals(q.getRedisEpoch(),r.getRedisEpoch())
                || !Objects.equals(q.getSkuId(),r.getSkuId()) || !Objects.equals(q.getQuantity(),r.getQuantity())
                || !Objects.equals(q.getPriceCent(),r.getPriceCent()) || !Objects.equals(q.getGoodsAmount(),r.getGoodsAmount())
                || !Objects.equals(q.getShippingAmount(),r.getShippingAmount()) || !Objects.equals(q.getPayAmount(),r.getPayAmount())
                || !Objects.equals(q.getExpiresAt(),utc(r.getQuoteExpiresAt())) || utc(r.getAcceptedAt()).isBefore(q.getCreatedAt())) throw incomplete();
    }
    private TradeSeckillInboxDO sameReceipt(TradeSeckillInboxDO r,SeckillEventDTO e,String hash) {
        if (!Objects.equals(r.getTenantId(),e.getTenantId()) || !Objects.equals(r.getEventId(),e.getEventId())
                || !Objects.equals(r.getRequestNo(),e.getRequestNo()) || !Objects.equals(r.getAggregateVersion(),e.getAggregateVersion())
                || !Objects.equals(r.getEventType(),e.getEventType()) || !Objects.equals(r.getPayloadHash(),hash)) throw exception0(409,"原消息号不可更换参数");
        return r;
    }
    private Long context(SeckillEventDTO e) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("秒杀建单只能在事务外可信后台执行");
        Long tenant=TenantContextHolder.getTenantId();
        if (!Arrays.asList("SECKILL_CREATE","SECKILL_CLOSE").contains(e.getEventType())) throw exception0(400,"需要建单或建单前关闭事件");
        if (!id(tenant) || TenantContextHolder.isIgnore() || !Objects.equals(tenant,e.getTenantId())) throw exception0(403,"建单租户上下文不正确");return tenant;
    }
    private static LocalDateTime utc(OffsetDateTime x) { return LocalDateTime.ofInstant(x.toInstant(),ZoneOffset.UTC); }
    private static LocalDateTime shanghai(OffsetDateTime x) { return LocalDateTime.ofInstant(x.toInstant(),ZoneId.of("Asia/Shanghai")); }
    private boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原秒杀申请、绑定或订单事实不一致，请按原申请核查"); }
}
