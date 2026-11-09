package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillOutboxDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillOutboxPublishMapper;
import cn.qinghe.mall.module.promotion.framework.mq.SeckillEventConfiguration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.LocalDateTime;
import java.util.UUID;

/** 直接择取TradeOrderEventPublisher领取→事务外syncSend→带原租约ACK，至少一次投递。 @author 芋道源码 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-events",name="enabled",havingValue="true")
public class SeckillEventPublisher {
    private final SeckillOutboxPublishMapper records;
    private final RocketMQTemplate rocket;
    private final SeckillEventConfiguration.Properties properties;
    private final TransactionTemplate write;
    public SeckillEventPublisher(SeckillOutboxPublishMapper records,@Qualifier("promotionSeckillRocketTemplate") RocketMQTemplate rocket,
            SeckillEventConfiguration.Properties properties,PlatformTransactionManager manager) {
        this.records=records;this.rocket=rocket;this.properties=properties;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }
    public Result publish(int batch,String jobTrace) {
        Long tenant=TenantContextHolder.getTenantId();
        if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null) throw new IllegalStateException("秒杀消息发布仅允许事务外可信后台调度");
        if (tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore() || batch<1 || batch>20
                || jobTrace==null || !jobTrace.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("秒杀消息租户、批次或trace不正确");
        write.executeWithoutResult(tx -> records.exhaust(tenant,records.now()));
        Result result=new Result();long started=System.nanoTime();
        for (int i=0;i<batch && System.nanoTime()-started<45000000000L;i++) {
            SeckillOutboxDO row=write.execute(tx -> {
                LocalDateTime now=records.now();SeckillOutboxDO original=records.next(tenant,now);
                if (original==null) return null;
                String owner=UUID.randomUUID().toString();
                if (records.claim(tenant,original.getEventId(),original.getAttempts(),owner,now.plusSeconds(60),now)!=1)
                    throw new IllegalStateException("原秒杀事件领取已变化");
                original.setLeaseOwner(owner);original.setAttempts(original.getAttempts()+1);return original;
            });
            if (row==null) break;result.attempted++;
            boolean acknowledged=false;
            try {
                String body=SeckillEventEnvelope.encode(SeckillEventReadService.frame(row,tenant));
                Message<String> message=MessageBuilder.withPayload(body).setHeader("KEYS",row.getEventId()).setHeader("tenant-id",tenant.toString()).build();
                SendResult sent=rocket.syncSend(properties.getTopic(),message,3000L); // 此处没有数据库事务或锁。
                if (sent==null || sent.getSendStatus()!=SendStatus.SEND_OK) throw new IllegalStateException("Broker未确认秒杀事件存储");
                acknowledged=true;
                Integer count=write.execute(tx -> records.published(tenant,row.getEventId(),row.getLeaseOwner(),records.now()));
                if (count==null || count!=1) throw new IllegalStateException("原秒杀事件本地确认未完成");result.published++;
            } catch (RuntimeException uncertain) {
                result.failed++;
                // Broker已确认后本地ACK未知，保留原租约，过期重投同一eventId，不伪报从未发送。
                if (!acknowledged) write.executeWithoutResult(tx -> {
                    LocalDateTime now=records.now();long delay=Math.min(900L,15L*(1L<<(row.getAttempts()-1)));
                    if (records.failed(tenant,row.getEventId(),row.getLeaseOwner(),now.plusSeconds(delay),now)!=1)
                        throw new IllegalStateException("原秒杀事件投递仍待原号核对");
                });
                log.warn("Seckill event delivery pending tenant={},request={},event={},attempt={},trace={}",tenant,row.getRequestNo(),row.getEventId(),row.getAttempts(),jobTrace);
            }
        }
        result.manual=records.manual(tenant);result.overdue=records.overdue(tenant,records.now());
        if (result.manual>0 || result.overdue>0) log.error("Seckill event backlog tenant={},manual={},overdue={},trace={}",tenant,result.manual,result.overdue,jobTrace);
        return result;
    }
    @Data public static class Result { private int attempted;private int published;private int failed;private long manual;private long overdue; }
}
