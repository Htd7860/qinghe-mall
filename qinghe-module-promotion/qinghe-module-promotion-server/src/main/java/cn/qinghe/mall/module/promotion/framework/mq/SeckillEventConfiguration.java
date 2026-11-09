package cn.qinghe.mall.module.promotion.framework.mq;
import lombok.Data;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;

/** 直接复用trade RocketMQ显式配置方式；默认不创建客户端，不启动Broker或注册任务。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-events",name="enabled",havingValue="true")
@EnableConfigurationProperties(SeckillEventConfiguration.Properties.class)
public class SeckillEventConfiguration {
    @Bean
    public RocketMQTemplate promotionSeckillRocketTemplate(Properties properties) {
        DefaultMQProducer producer=new DefaultMQProducer(properties.getProducerGroup());producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(3000);producer.setRetryTimesWhenSendFailed(0);producer.setRetryAnotherBrokerWhenNotStoreOK(false);
        producer.setMaxMessageSize(4096);RocketMQTemplate template=new RocketMQTemplate();template.setProducer(producer);return template;
    }
    @Data @Validated @ConfigurationProperties(prefix="qinghe.promotion.seckill-events")
    public static class Properties {
        @NotBlank private String nameServer;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String topic="qinghe-seckill-commands";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String producerGroup="qinghe-seckill-outbox";
    }
}
