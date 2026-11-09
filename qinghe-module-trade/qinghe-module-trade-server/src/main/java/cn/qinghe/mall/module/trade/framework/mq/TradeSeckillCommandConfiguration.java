package cn.qinghe.mall.module.trade.framework.mq;

import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillCommandConsumer;
import lombok.Data;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;

/** 直接复用现有TradeEventConfiguration消费参数；只有明确开关启用才创建Broker客户端。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="qinghe.trade.seckill-orders",name="enabled",havingValue="true")
@EnableConfigurationProperties(TradeSeckillCommandConfiguration.Properties.class)
public class TradeSeckillCommandConfiguration {
    @Bean(initMethod="start",destroyMethod="shutdown")
    public DefaultMQPushConsumer tradeSeckillCommandPushConsumer(Properties p,TradeSeckillCommandConsumer listener,
            @Value("${qinghe.trade.seckill-results.topic:qinghe-seckill-results}") String resultTopic,
            @Value("${qinghe.trade.events.consumer-group:qinghe-trade-recovery-v1}") String ordinaryGroup) throws Exception {
        if (p.getTopic().equals(resultTopic) || p.getConsumerGroup().equals(ordinaryGroup))
            throw new IllegalArgumentException("秒杀命令须使用独立Topic和消费组");
        DefaultMQPushConsumer consumer=new DefaultMQPushConsumer(p.getConsumerGroup());
        consumer.setNamesrvAddr(p.getNameServer());consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(2);consumer.setConsumeThreadMax(4);
        consumer.setConsumeMessageBatchMaxSize(1);consumer.setPullBatchSize(16);
        consumer.setPullThresholdForQueue(100);consumer.setPullThresholdSizeForQueue(4);
        consumer.setConsumeTimeout(1);consumer.setMaxReconsumeTimes(10);consumer.setAwaitTerminationMillisWhenShutdown(10000);
        consumer.subscribe(p.getTopic(),"*");consumer.registerMessageListener(listener);return consumer;
    }
    @Data @Validated @ConfigurationProperties(prefix="qinghe.trade.seckill-orders")
    public static class Properties {
        @NotBlank private String nameServer;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String topic="qinghe-seckill-commands";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String consumerGroup="qinghe-seckill-create-v1";
    }
}
