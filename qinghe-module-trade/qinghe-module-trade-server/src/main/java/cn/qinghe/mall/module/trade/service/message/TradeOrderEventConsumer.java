package cn.qinghe.mall.module.trade.service.message;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderEventConsumeMapper;
import cn.qinghe.mall.module.trade.framework.mq.TradeEventConfiguration;
import cn.qinghe.mall.module.trade.service.order.TradeOrderRecoveryService;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillOrderCloseService;
import org.springframework.beans.factory.ObjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 择取原 onMessage -> 业务委托、租户设置/清理职责。
 * 本地消费回执和恢复意图同事务；不在Broker回调中调用渠道、库存或授权退款。
 * @author 芋道源码
 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.trade.events",name="enabled",havingValue="true")
public class TradeOrderEventConsumer implements MessageListenerConcurrently {
    private final TradeOrderEventConsumeMapper records;
    private final TradeEventConfiguration.Properties properties;
    private final TradeOrderRecoveryService recovery;
    private final ObjectProvider<TradeSeckillOrderCloseService> seckillClosures;
    private final TransactionTemplate write;

    public TradeOrderEventConsumer(TradeOrderEventConsumeMapper records,TradeEventConfiguration.Properties properties,
            PlatformTransactionManager manager,TradeOrderRecoveryService recovery,
            ObjectProvider<TradeSeckillOrderCloseService> seckillClosures) {
        this.records=records;this.properties=properties;
        this.recovery=recovery;
        this.seckillClosures=seckillClosures;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,ConsumeConcurrentlyContext context) {
        TenantContextHolder.clear();SecurityContextHolder.clearContext();clearLogContext();
        try {
            if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                    || messages==null || messages.size()!=1)throw new IllegalStateException("消费仅允许单条后台消息");
            MessageExt message=messages.get(0);
            if(message==null || !(properties.getTopic().equals(message.getTopic()) || properties.getExpiryTopic().equals(message.getTopic())) || message.getBody()==null
                    || message.getBody().length==0 || message.getBody().length>65536)
                throw new IllegalArgumentException("交易消息来源或大小不正确");
            String header=message.getUserProperty("tenant-id");
            if(header==null || !header.matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException("消息缺少原租户");
            Long tenant=Long.valueOf(header);
            if(tenant>9_007_199_254_740_991L)throw new IllegalArgumentException("消息租户不正确");
            String body=new String(message.getBody(),StandardCharsets.UTF_8);
            Map<?,?> value=JsonUtils.parseObject(body,Map.class);
            Object identity=value==null?null:value.get("eventId");
            if(!(identity instanceof String) || !((String)identity).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                    || !identity.equals(message.getKeys()))throw new IllegalArgumentException("消息缺少原事件号");
            String event=(String)identity;
            MDC.put("tenantId",header);MDC.put("eventId",event);
            // 消息头只是定位线索；必须与本服务持久原事件完整一致才允许登记业务动作。
            TenantUtils.execute(tenant,() -> {
                Long expiryOrder=write.execute(tx -> {
                    TradeOrderOutboxDO original=records.original(tenant,event);
                    if(original==null)throw new IllegalStateException("原交易事件不存在");
                    String canonical=TradeOrderEventEnvelope.encode(original,tenant);
                    if(!Arrays.equals(canonical.getBytes(StandardCharsets.UTF_8),message.getBody()))
                        throw new IllegalArgumentException("消息与原事件不一致");
                    boolean expiry="ORDER_EXPIRE_CHECK".equals(original.getEventType());
                    if(!(expiry?properties.getExpiryTopic():properties.getTopic()).equals(message.getTopic()))
                        throw new IllegalArgumentException("原事件与消息Topic类型不一致");
                    Map<?,?> verified=JsonUtils.parseObject(canonical,Map.class);
                    MDC.put("traceId",(String)verified.get("traceId"));
                    MDC.put("orderId",original.getPreparationId().toString());
                    String hash=DigestUtil.sha256Hex(canonical);
                    LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
                    int inserted=records.receipt(tenant,event,hash,now);
                    if(inserted==0) {
                        if(!hash.equals(records.receiptHash(tenant,event)))throw new IllegalStateException("原消费回执不一致");
                        return expiry?original.getPreparationId():null;
                    }
                    if(inserted!=1)throw new IllegalStateException("消费回执未确认");
                    Long order=original.getPreparationId();
                    // 只重读当前业务状态登记缺失动作；旧消息不把状态回写为旧版本，也不重置MANUAL任务。
                    boolean seckill=seckillClosures.getIfAvailable()!=null;
                    int tasks=records.seedPay(tenant,order,now,seckill)+records.seedCancel(tenant,order,now,seckill)
                            +records.seedRelease(tenant,order,now,seckill)+records.seedCompensate(tenant,order,now)
                            +records.seedReceive(tenant,order,now);
                    if(expiry)tasks+=records.seedExpiry(tenant,order,now,seckill);
                    if(original.getEventType().startsWith("AFTERSALE_")) {
                        Long afterSale=Long.valueOf(verified.get("afterSaleId").toString());
                        tasks+=records.seedReturn(tenant,order,afterSale,now)+records.seedRefund(tenant,order,afterSale,now);
                    }
                    if(tasks<0 || tasks>7)throw new IllegalStateException("恢复动作登记数量不正确");
                    if(tasks>0 && records.tasks(tenant,event,tasks)!=1)throw new IllegalStateException("消费回执结果未确认");
                    return expiry?order:null;
      
            });
              // 已提交原消费回执和恢复行，再触发同一业务；失败由持久任务继续，不撤回消费事实。
              if(expiryOrder!=null) {
                  try {recovery.recoverExpiry(expiryOrder,event);}
                  catch(RuntimeException pending) {
                      log.warn("到期消费已提交，业务等待任务恢复 tenant={}, order={}, event={}, code=503",tenant,expiryOrder,event);
                  }
              }
              return null;
            });
            // execute已经完成提交；提交失败/未知也不会返回成功，重复投递由同库唯一键收敛。
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch(RuntimeException error) {
            log.warn("交易事件消费待恢复 tenant={}, event={}, order={}, trace={}, code=503",
                    MDC.get("tenantId"),MDC.get("eventId"),MDC.get("orderId"),MDC.get("traceId"));
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        } finally {
            TenantContextHolder.clear();SecurityContextHolder.clearContext();clearLogContext();
        }
    }

    private void clearLogContext() {
        MDC.remove("tenantId");MDC.remove("eventId");MDC.remove("orderId");MDC.remove("traceId");
    }
}
