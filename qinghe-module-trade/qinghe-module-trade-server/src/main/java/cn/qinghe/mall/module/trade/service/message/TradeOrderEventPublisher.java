package cn.qinghe.mall.module.trade.service.message;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderOutboxMapper;
import cn.qinghe.mall.module.trade.framework.mq.TradeEventConfiguration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 择取原RocketMQ同步发送，复用业务已提交Outbox；至少一次投递，消费者须原eventId幂等。
 * @author 芋道源码
 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.trade.events",name="enabled",havingValue="true")
public class TradeOrderEventPublisher {
    private final TradeOrderOutboxMapper records;
    private final RocketMQTemplate rocket;
    private final TradeEventConfiguration.Properties properties;
    private final TransactionTemplate write;
    public TradeOrderEventPublisher(TradeOrderOutboxMapper records,@Qualifier("tradeOrderRocketTemplate") RocketMQTemplate rocket,
            TradeEventConfiguration.Properties properties,PlatformTransactionManager manager) {
        this.records=records;this.rocket=rocket;this.properties=properties;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }
    public Result publish(int batch,String jobTrace) {
        Long tenant=TenantContextHolder.getTenantId();
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("事件发布仅允许事务外可信调度");
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore() || batch<1 || batch>20
                || jobTrace==null || !jobTrace.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("事件发布租户/批次不正确");
        write.executeWithoutResult(tx -> {
            // 部署前已有未付单补齐同一事件；NOT EXISTS+原业务唯一键，最多20条，不改业务状态。
            for(TradeOrderDO order:records.expiryCandidates(tenant)) {
                String payload=JsonUtils.toJsonString(Collections.singletonMap("dueAt",TradeOrderEventEnvelope.epochCeiling(order.getPayDeadline())));
                records.insertExpiry(tenant,order.getId(),UUID.randomUUID().toString(),payload,order.getPayDeadline(),now());
            }
            records.exhaust(tenant,now());
        });
        Result result=new Result();long started=System.nanoTime();
        for(int i=0;i<batch && System.nanoTime()-started<45_000_000_000L;i++) {
            TradeOrderOutboxDO row=write.execute(tx -> {
                LocalDateTime instant=now();TradeOrderOutboxDO value=records.next(tenant,instant);
                if(value==null)return null;
                String owner=UUID.randomUUID().toString();
                if(records.claim(tenant,value.getEventId(),value.getAttempts(),owner,instant.plusSeconds(60),instant)!=1)
                    throw new IllegalStateException("原事件领取已变化");
                value.setLeaseOwner(owner);value.setAttempts(value.getAttempts()+1);return value;
            });
            if(row==null)break;result.attempted++;
            boolean acknowledged=false;
            try {
                String body=TradeOrderEventEnvelope.encode(row,tenant);
                // 与原版syncSend一致，使用明确3秒超时；发送期间没有数据库事务或锁。
                Message<String> message=MessageBuilder.withPayload(body).setHeader("KEYS",row.getEventId()).setHeader("tenant-id",tenant.toString()).build();
                SendResult sent;
                if("ORDER_EXPIRE_CHECK".equals(row.getEventType())) {
                    long due=((Number)JsonUtils.parseObject(body,Map.class).get("dueAt")).longValue();
                    // 固定原绝对期限；重投不重新计30分钟，已到期由Broker立即投递。
                    sent=rocket.syncSendDeliverTimeMills(properties.getExpiryTopic(),message,due);
                } else sent=rocket.syncSend(properties.getTopic(),message,3000L);
                if(sent==null || sent.getSendStatus()!=SendStatus.SEND_OK)throw new IllegalStateException("Broker未确认事件存储成功");
                acknowledged=true;
                Integer count=write.execute(tx -> records.published(tenant,row.getEventId(),row.getLeaseOwner(),now()));
                if(count==null || count!=1)throw new IllegalStateException("原事件本地确认未完成");
                result.published++;
            }catch(RuntimeException error) {
                result.failed++;
                // Broker已确认而本地落账异常时保留租约，过期后按原eventId重发，不能伪称未发送。
                if(!acknowledged)write.executeWithoutResult(tx -> {
                    LocalDateTime instant=now();long delay=Math.min(900L,15L*(1L<<(row.getAttempts()-1)));
                    if(records.failed(tenant,row.getEventId(),row.getLeaseOwner(),instant.plusSeconds(delay),instant)!=1)
                        throw new IllegalStateException("原事件发送结果待核对");
                });
                log.warn("交易事件投递待核对 tenant={}, event={}, attempt={}, code=503, trace={}",tenant,row.getEventId(),row.getAttempts(),jobTrace);
            }
        }
        result.manual=records.manual(tenant);result.overdue=records.overdue(tenant,now());
        if(result.manual>0 || result.overdue>0)log.error("交易事件积压 tenant={}, manual={}, overdue={}, trace={}",tenant,result.manual,result.overdue,jobTrace);
        return result;
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
    @Data public static class Result {private int attempted;private int published;private int failed;private long manual;private long overdue;}
}
