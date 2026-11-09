package cn.qinghe.mall.module.trade.service.message;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import java.time.ZoneId;
import java.util.*;

/** 固定交易事件信封；同一实现供发布与消费原账核对，不携带业务命令或财务授权。 */
public final class TradeOrderEventEnvelope {
    private TradeOrderEventEnvelope() {}
    public static String encode(TradeOrderOutboxDO row,Long tenant) {
        if(!Objects.equals(row.getTenantId(),tenant) || row.getEventId()==null
                || !row.getEventId().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || row.getPreparationId()==null || row.getPreparationId()<=0 || row.getPreparationId()>9_007_199_254_740_991L
                || row.getAggregateVersion()==null || row.getAggregateVersion()<0 || row.getAggregateVersion()>9_007_199_254_740_991L
                || !Integer.valueOf(1).equals(row.getSchemaVersion()) || row.getCreatedAt()==null
                || !Arrays.asList("ORDER_PREPARED","ORDER_DELIVERED","ORDER_RECEIVED","AFTERSALE_APPLIED","AFTERSALE_DECIDED","ORDER_EXPIRE_CHECK").contains(row.getEventType()))
            throw new IllegalStateException("原事件身份或版本不完整");
        if(row.getPayload()==null || row.getPayload().length()>65536)throw new IllegalStateException("原事件数据大小不正确");
        Map<?,?> original=JsonUtils.parseObject(row.getPayload(),Map.class);
        if(original==null)throw new IllegalStateException("原事件数据不完整");
        Object sourceTrace=original.get("traceId");String trace=sourceTrace instanceof String && ((String)sourceTrace).matches("[A-Za-z0-9_-]{1,64}")?(String)sourceTrace:row.getEventId();
        Map<String,Object> value=new LinkedHashMap<>();value.put("eventId",row.getEventId());value.put("tenantId",tenant);
        value.put("orderId",row.getPreparationId());value.put("eventType",row.getEventType());value.put("aggregateVersion",row.getAggregateVersion());
        value.put("schemaVersion",1);value.put("occurredAt",row.getCreatedAt().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());value.put("traceId",trace);
        if("ORDER_EXPIRE_CHECK".equals(row.getEventType())) {
            Object due=original.get("dueAt");
            if(!(due instanceof Number) || !due.toString().matches("[1-9][0-9]{0,15}")
                    || Long.parseLong(due.toString())>9_007_199_254_740_991L)
                throw new IllegalStateException("原到期事件缺少付款期限");
            value.put("dueAt",Long.valueOf(due.toString()));
        }
        if(row.getEventType().startsWith("AFTERSALE_")) {
            Object id=original.get("afterSaleId");
            if(!(id instanceof Number) || !id.toString().matches("[1-9][0-9]{0,15}") || Long.parseLong(id.toString())>9_007_199_254_740_991L)
                throw new IllegalStateException("原售后事件缺少原申请号");
            value.put("afterSaleId",Long.valueOf(id.toString()));
        }
        // 不转发原payload中的原因、地址、金额或其他任意字段；消费者按原身份重新读取权威状态。
        return JsonUtils.toJsonString(value);
    }
    /** MySQL微秒期限转Broker毫秒时间戳时向上取整，不把期限提前不足1毫秒。 */
    public static long epochCeiling(java.time.LocalDateTime deadline) {
        return Math.addExact(deadline.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                deadline.getNano()%1_000_000==0?0L:1L);
    }
}
