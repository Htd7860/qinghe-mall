package cn.qinghe.mall.module.trade.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.promotion.api.seckill.SeckillEventEnvelope;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillEventDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillInboxDO;
import cn.qinghe.mall.module.trade.framework.mq.TradeSeckillCommandConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 直接择取TradeOrderEventConsumer单条校验、业务委托和finally清理；核心Service提交回执后才ACK。
 * @author 芋道源码
 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
public class TradeSeckillCommandConsumer implements MessageListenerConcurrently {
    private final TradeSeckillCommandConfiguration.Properties properties;
    private final TradeSeckillOrderCreationService service;
    public TradeSeckillCommandConsumer(TradeSeckillCommandConfiguration.Properties properties,TradeSeckillOrderCreationService service) { this.properties=properties;this.service=service; }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,ConsumeConcurrentlyContext context) {
        TenantContextHolder.clear();SecurityContextHolder.clearContext();clearLogContext();
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                    || messages==null || messages.size()!=1) throw new IllegalStateException("秒杀消费只允许单条后台消息");
            MessageExt message=messages.get(0);
            if (message==null || !properties.getTopic().equals(message.getTopic()) || message.getBody()==null
                    || message.getBody().length==0 || message.getBody().length>2048) throw new IllegalArgumentException("秒杀消息来源或大小不正确");
            String body=new String(message.getBody(),StandardCharsets.UTF_8);
            if (!Arrays.equals(body.getBytes(StandardCharsets.UTF_8),message.getBody())) throw new IllegalArgumentException("消息必须为有效UTF8");
            SeckillEventDTO event=SeckillEventEnvelope.decode(body);
            if (!Arrays.asList("SECKILL_CREATE","SECKILL_CLOSE").contains(event.getEventType()) || !event.getEventId().equals(message.getKeys())
                    || !event.getTenantId().toString().equals(message.getUserProperty("tenant-id"))) throw new IllegalArgumentException("秒杀消息类型、原事件号或租户不一致");
            Long tenant=event.getTenantId();
            MDC.put("tenantId",tenant.toString());MDC.put("eventId",event.getEventId());MDC.put("requestNo",event.getRequestNo());MDC.put("traceId",event.getEventId());
            // 头字段只是定位；Service必须验证来源库原事件/事实，或读取本库已提交且同hash的最终回执。
            TradeSeckillInboxDO receipt=TenantUtils.execute(tenant,() -> service.process(body));
            if (receipt==null || !Objects.equals(receipt.getTenantId(),tenant) || !Objects.equals(receipt.getEventId(),event.getEventId())
                    || !Objects.equals(receipt.getRequestNo(),event.getRequestNo()) || receipt.getProcessedAt()==null)
                throw new IllegalStateException("秒杀消息没有对应的最终消费回执");
            if (receipt.getOrderId()!=null) MDC.put("orderId",receipt.getOrderId().toString());
            log.info("Seckill command consumed tenant={},request={},event={},order={},trace={}",tenant,event.getRequestNo(),event.getEventId(),receipt.getOrderId(),event.getEventId());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException pending) {
            // 不输出消息全文、地址、Token或异常原文；原事件仍由Broker重投/进入DLQ后按原号恢复。
            log.warn("Seckill command pending tenant={},request={},event={},order={},trace={},code=503",MDC.get("tenantId"),MDC.get("requestNo"),MDC.get("eventId"),MDC.get("orderId"),MDC.get("traceId"));
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        } finally {
            TenantContextHolder.clear();SecurityContextHolder.clearContext();clearLogContext();
        }
    }
    private void clearLogContext() {
        for (String key:Arrays.asList("tenantId","eventId","orderId","traceId","requestNo","requestId")) MDC.remove(key);
    }
}
