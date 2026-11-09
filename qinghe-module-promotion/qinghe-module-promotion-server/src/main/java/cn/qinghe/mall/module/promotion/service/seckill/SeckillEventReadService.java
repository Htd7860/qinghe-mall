package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 消息只作定位，由来源库同一RR快照核对原Outbox、受理意图10和真实申请，不写状态。 */
@Service @PreAuthorize("hasAuthority('internal:trade:order-command')")
public class SeckillEventReadService {
    private final SeckillOutboxPublishMapper events;
    private final SeckillAdmissionMapper admissions;
    private final SeckillAdmissionIntentMapper intents;
    private final TransactionTemplate read;
    public SeckillEventReadService(SeckillOutboxPublishMapper events,SeckillAdmissionMapper admissions,SeckillAdmissionIntentMapper intents,PlatformTransactionManager manager) {
        this.events=events;this.admissions=admissions;this.intents=intents;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public SeckillDispatchSnapshotDTO get(SeckillEventLookupReqDTO request) {
        Long tenant=TenantContextHolder.getTenantId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("原事件读取不能嵌套外层事务");
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()) throw exception0(403,"需要可信当前租户");
        if (request==null || request.getEventId()==null || !request.getEventId().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw exception0(400,"需要原事件号");
        return read.execute(tx -> {
            SeckillOutboxDO event=events.original(tenant,request.getEventId());
            if (event==null) throw exception0(404,"原秒杀事件不存在");
            SeckillEventDTO verified=frame(event,tenant);
            // 不要求PUBLISHED：合法消息可能已经到Broker，但来源库ACK尚未落账。
            SeckillRequestDO source=admissions.forDispatch(tenant,event.getRequestNo());
            if (source==null) throw exception0(503,"原持久受理申请缺失");
            SeckillRequestReceiptService.project(source); // 复用状态/金额/原120秒与30分钟期限验证。
            SeckillAdmissionIntentDO intent=intents.get(tenant,source.getMemberId(),source.getClientRequestId());
            if (intent==null || !Integer.valueOf(10).equals(intent.getStatus()) || !Objects.equals(intent.getRequestNo(),source.getRequestNo())
                    || !Objects.equals(intent.getRequestDigest(),source.getRequestDigest()) || source.getVersion()<event.getAggregateVersion())
                throw exception0(503,"原事件没有对应的持久受理事实");
            SeckillRequestDO frozen=SeckillAdmissionIntentService.frozen(intent);
            if (!Objects.equals(frozen.getQuoteId(),source.getQuoteId()) || !Objects.equals(frozen.getQuoteDigest(),source.getQuoteDigest())
                    || !Objects.equals(frozen.getActivityId(),source.getActivityId()) || !Objects.equals(frozen.getGeneration(),source.getGeneration())
                    || !Objects.equals(frozen.getSkuId(),source.getSkuId())) throw exception0(503,"原申请引用已变化");
            SeckillAcceptedRequestDTO value=new SeckillAcceptedRequestDTO();value.setTenantId(source.getTenantId());value.setRequestNo(source.getRequestNo());
            value.setMemberId(source.getMemberId());value.setClientRequestId(source.getClientRequestId());value.setRequestDigest(source.getRequestDigest());
            value.setActivityId(source.getActivityId());value.setGeneration(source.getGeneration());value.setActivityVersion(source.getActivityVersion());
            value.setRedisEpoch(source.getRedisEpoch());value.setSkuId(source.getSkuId());value.setQuantity(source.getQuantity());value.setPriceCent(source.getPriceCent());
            value.setGoodsAmount(source.getGoodsAmount());value.setShippingAmount(source.getShippingAmount());value.setPayAmount(source.getPayAmount());
            value.setQuoteId(source.getQuoteId());value.setQuoteDigest(source.getQuoteDigest());value.setStatus(source.getStatus());
            value.setOrderId(source.getOrderId());value.setFenceVersion(source.getFenceVersion());value.setVersion(source.getVersion());
            value.setQuoteExpiresAt(source.getQuoteExpiresTime().atOffset(ZoneOffset.UTC));value.setAcceptedAt(source.getAcceptedTime().atOffset(ZoneOffset.UTC));
            value.setReconcileDeadline(source.getReconcileDeadline().atOffset(ZoneOffset.UTC));value.setPayDeadline(source.getPayDeadline().atOffset(ZoneOffset.UTC));
            SeckillDispatchSnapshotDTO result=new SeckillDispatchSnapshotDTO();result.setEvent(verified);result.setRequest(value);return result;
        });
    }
    /** 原交易信封固定投影职责；纯转换供发布和权威读取共同调用，不访问DB或外部服务。 */
    public static SeckillEventDTO frame(SeckillOutboxDO row,Long tenant) {
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || row==null || !Objects.equals(row.getTenantId(),tenant) || row.getCreatedAt()==null || row.getPayload()==null || row.getPayload().length()>2048)
            throw new IllegalStateException("原秒杀事件不完整");
        Map<?,?> payload=JsonUtils.parseObject(row.getPayload(),Map.class);
        if (payload==null || payload.size()!=4 || !Integer.valueOf(1).equals(row.getSchemaVersion())
                || !(payload.get("schemaVersion") instanceof Number) || !"1".equals(payload.get("schemaVersion").toString())
                || !(payload.get("tenantId") instanceof Number) || !tenant.toString().equals(payload.get("tenantId").toString())
                || !Objects.equals(payload.get("requestNo"),row.getRequestNo()) || !(payload.get("aggregateVersion") instanceof Number)
                || row.getAggregateVersion()==null || !row.getAggregateVersion().toString().equals(payload.get("aggregateVersion").toString()))
            throw new IllegalStateException("原秒杀事件引用不一致");
        SeckillEventDTO event=new SeckillEventDTO();event.setEventId(row.getEventId());event.setTenantId(tenant);event.setRequestNo(row.getRequestNo());
        event.setEventType(row.getEventType());event.setAggregateVersion(row.getAggregateVersion());event.setSchemaVersion(row.getSchemaVersion());
        event.setOccurredAt(row.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());SeckillEventEnvelope.encode(event);return event;
    }
}
