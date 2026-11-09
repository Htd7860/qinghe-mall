package cn.qinghe.mall.module.promotion.framework.mq;

import cn.qinghe.mall.module.promotion.service.seckill.SeckillOrderResultConsumer;
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

/** 直接复用既有显式消费配置；数据库结果和缓存待同步进度已持久化后才交Broker确认。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
@EnableConfigurationProperties(SeckillResultConsumerConfiguration.Properties.class)
public class SeckillResultConsumerConfiguration {
    @Bean(initMethod="start",destroyMethod="shutdown")
    public DefaultMQPushConsumer promotionSeckillResultPushConsumer(Properties p,SeckillOrderResultConsumer listener,
            @Value("${qinghe.promotion.seckill-events.topic:qinghe-seckill-commands}") String commandTopic) throws Exception {
        if (p.getTopic().equals(commandTopic)) throw new IllegalArgumentException("秒杀结果须使用独立Topic");
        DefaultMQPushConsumer consumer=new DefaultMQPushConsumer(p.getConsumerGroup());
        consumer.setNamesrvAddr(p.getNameServer());consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(2);consumer.setConsumeThreadMax(4);
        consumer.setConsumeMessageBatchMaxSize(1);consumer.setPullBatchSize(16);
        consumer.setPullThresholdForQueue(100);consumer.setPullThresholdSizeForQueue(4);
        consumer.setConsumeTimeout(1);consumer.setMaxReconsumeTimes(10);consumer.setAwaitTerminationMillisWhenShutdown(10000);
        consumer.subscribe(p.getTopic(),"*");consumer.registerMessageListener(listener);return consumer;
    }
    @Data @Validated @ConfigurationProperties(prefix="qinghe.promotion.seckill-results")
    public static class Properties {
        @NotBlank private String nameServer;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String topic="qinghe-seckill-results";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String consumerGroup="qinghe-seckill-result-v1";
    }
}
