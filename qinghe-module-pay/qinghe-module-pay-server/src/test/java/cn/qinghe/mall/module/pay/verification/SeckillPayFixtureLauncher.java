package cn.qinghe.mall.module.pay.verification;

import cn.qinghe.mall.module.pay.PayServerApplication;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClientFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import java.net.InetSocketAddress;

/** DEC-077 only: shared test vendor, exact isolated database/profile/container guard. */
public final class SeckillPayFixtureLauncher {
    public static void main(String[] args) {
        String db=System.getenv("QINGHE_PAY_DB_URL");
        if(db==null || !db.startsWith("jdbc:mysql://127.0.0.1:3306/qinghe_pay_seckill_test?")
                || !"seckill-verification".equals(System.getenv("SPRING_PROFILES_ACTIVE"))
                || !"38086".equals(System.getenv("PAY_PORT"))
                || !"true".equals(System.getenv("SECKILL_ISOLATED_CONTAINER"))) {
            throw new IllegalStateException("Dedicated seckill pay fixture guard");
        }
        ConfigurableApplicationContext context=SpringApplication.run(
                new Class<?>[]{PayServerApplication.class,FixtureConfiguration.class},args);
        if(!(context.getBean(PayClientFactory.class) instanceof CommercePayFixtureLauncher.FixtureFactory)) {
            context.close();throw new IllegalStateException("Test vendor was not selected");
        }
    }
    @Configuration @Profile("seckill-verification")
    public static class FixtureConfiguration {
        @Bean @Primary public PayClientFactory seckillVerificationPayClientFactory() throws Exception {
            // Wildcard is confined to the batch's internal Docker namespace; host mapping is loopback only.
            return new CommercePayFixtureLauncher.FixtureFactory(
                    System.getenv("SECKILL_FIXTURE_CONTROL_TOKEN"),new InetSocketAddress("0.0.0.0",38089));
        }
    }
}
