package cn.qinghe.mall.module.trade.framework.mq;

import lombok.Data;
import cn.qinghe.mall.module.trade.service.message.TradeOrderEventConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;

/** 原RocketMQ发送/消费职责；显式启用才创建客户端，不自动注册XXL任务或启动Broker。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="qinghe.trade.events",name="enabled",havingValue="true")
@EnableConfigurationProperties(TradeEventConfiguration.Properties.class)
public class TradeEventConfiguration {
    @Bean(initMethod="start",destroyMethod="shutdown")
    public DefaultMQPushConsumer tradeOrderRecoveryConsumer(Properties properties,TradeOrderEventConsumer listener) throws Exception {
        DefaultMQPushConsumer consumer=new DefaultMQPushConsumer(properties.getConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(2);consumer.setConsumeThreadMax(4);
        consumer.setConsumeMessageBatchMaxSize(1);consumer.setPullBatchSize(16);
        consumer.setPullThresholdForQueue(100);consumer.setPullThresholdSizeForQueue(4);
        consumer.setConsumeTimeout(1);consumer.setMaxReconsumeTimes(10);
        consumer.setAwaitTerminationMillisWhenShutdown(10000);
        consumer.subscribe(properties.getTopic(),"*");consumer.registerMessageListener(listener);
        if(properties.getTopic().equals(properties.getExpiryTopic()))throw new IllegalArgumentException("普通事件与到期事件须使用不同类型Topic");
        consumer.subscribe(properties.getExpiryTopic(),"*");
        return consumer;
    }
    @Bean
    public RocketMQTemplate tradeOrderRocketTemplate(Properties properties) {
        DefaultMQProducer producer=new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(0);producer.setRetryAnotherBrokerWhenNotStoreOK(false);
        producer.setMaxMessageSize(65536);
        RocketMQTemplate template=new RocketMQTemplate();template.setProducer(producer);return template;
    }
    @Data @Validated @ConfigurationProperties(prefix="qinghe.trade.events")
    public static class Properties {
        @NotBlank private String nameServer;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String topic="qinghe-trade-events";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String expiryTopic="qinghe-trade-expiry";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String producerGroup="qinghe-trade-outbox";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String consumerGroup="qinghe-trade-recovery-v1";
    }
}
