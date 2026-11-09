package cn.qinghe.mall.module.promotion.api.seckill;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillEventDTO;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 直接沿用现有TradeOrderEventEnvelope固定投影/原事件号trace方式，供两服务共用协议。 */
public final class SeckillEventEnvelope {
    private SeckillEventEnvelope() {}
    public static String encode(SeckillEventDTO event) {
        if (event==null || !uuid(event.getEventId()) || !uuid(event.getRequestNo()) || !id(event.getTenantId())
                || !Arrays.asList("SECKILL_CREATE","SECKILL_CLOSE","SECKILL_ORDER_CREATED","SECKILL_REQUEST_CLOSED").contains(event.getEventType())
                || event.getAggregateVersion()==null || event.getAggregateVersion()<0 || event.getAggregateVersion()>9007199254740991L
                || !Integer.valueOf(1).equals(event.getSchemaVersion()) || event.getOccurredAt()==null
                || event.getOccurredAt()<946684800000L || event.getOccurredAt()>4102444799999L) throw new IllegalArgumentException("秒杀原事件身份或版本不完整");
        Map<String,Object> value=new LinkedHashMap<>();value.put("eventId",event.getEventId());value.put("tenantId",event.getTenantId());
        value.put("requestNo",event.getRequestNo());value.put("eventType",event.getEventType());value.put("aggregateVersion",event.getAggregateVersion());
        value.put("schemaVersion",1);value.put("occurredAt",event.getOccurredAt());value.put("traceId",event.getEventId());
        return JsonUtils.toJsonString(value);
    }
    public static SeckillEventDTO decode(String body) {
        if (body==null || body.getBytes(StandardCharsets.UTF_8).length>2048) throw new IllegalArgumentException("秒杀消息大小不正确");
        Map<?,?> value=JsonUtils.parseObject(body,Map.class);
        if (value==null || value.size()!=8) throw new IllegalArgumentException("秒杀消息字段不正确");
        SeckillEventDTO event=new SeckillEventDTO();event.setEventId(text(value.get("eventId")));event.setTenantId(number(value.get("tenantId")));
        event.setRequestNo(text(value.get("requestNo")));event.setEventType(text(value.get("eventType")));event.setAggregateVersion(number(value.get("aggregateVersion")));
        if (!Long.valueOf(1).equals(number(value.get("schemaVersion")))) throw new IllegalArgumentException("秒杀消息协议不正确");
        event.setSchemaVersion(1);event.setOccurredAt(number(value.get("occurredAt")));
        if (!event.getEventId().equals(value.get("traceId")) || !encode(event).equals(body)) throw new IllegalArgumentException("秒杀消息与原规范信封不一致");
        return event;
    }
    private static String text(Object value) { if (!(value instanceof String)) throw new IllegalArgumentException("消息文本类型不正确");return (String)value; }
    private static Long number(Object value) {
        if (!(value instanceof Number) || !value.toString().matches("0|[1-9][0-9]{0,15}")) throw new IllegalArgumentException("消息整数类型不正确");
        long n=Long.parseLong(value.toString());if (n>9007199254740991L) throw new IllegalArgumentException("消息整数超出范围");return n;
    }
    private static boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private static boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
}
