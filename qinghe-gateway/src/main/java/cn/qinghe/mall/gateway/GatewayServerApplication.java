package cn.qinghe.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;

/** 浏览器统一入口；Bearer过滤器每次向system确认身份，HTTP与权限能力按实际路由开放。 */
@SpringBootApplication(exclude = ReactiveUserDetailsServiceAutoConfiguration.class)
public class GatewayServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServerApplication.class, args);
    }
}
