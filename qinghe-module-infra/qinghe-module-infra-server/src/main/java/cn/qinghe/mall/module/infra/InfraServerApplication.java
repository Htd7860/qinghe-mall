package cn.qinghe.mall.module.infra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** 独立Servlet基础设施服务，Bearer身份与权限通过system验证。 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class InfraServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(InfraServerApplication.class, args);
    }
}
