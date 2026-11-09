package cn.qinghe.mall.module.trade.framework.mq;
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
@ConditionalOnProperty(prefix="qinghe.trade.seckill-results",name="enabled",havingValue="true")
@EnableConfigurationProperties(TradeSeckillResultConfiguration.Properties.class)
public class TradeSeckillResultConfiguration {
    @Bean
    public RocketMQTemplate tradeSeckillResultRocketTemplate(Properties properties) {
        DefaultMQProducer producer=new DefaultMQProducer(properties.getProducerGroup());producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(3000);producer.setRetryTimesWhenSendFailed(0);producer.setRetryAnotherBrokerWhenNotStoreOK(false);
        producer.setMaxMessageSize(4096);RocketMQTemplate template=new RocketMQTemplate();template.setProducer(producer);return template;
    }
    @Data @Validated @ConfigurationProperties(prefix="qinghe.trade.seckill-results")
    public static class Properties {
        @NotBlank private String nameServer;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String topic="qinghe-seckill-results";
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,127}") private String producerGroup="qinghe-seckill-result-outbox";
    }
}
