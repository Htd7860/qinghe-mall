package cn.qinghe.mall.module.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system
 * @Project：qinghe-mall
 * @name：SystemServerApplication
 * @Date：2026/10/21 0:16
 * @Filename：SystemServerApplication
 */
@org.springframework.boot.context.properties.EnableConfigurationProperties({
        cn.qinghe.mall.module.system.framework.security.StorefrontProperties.class,
        cn.qinghe.mall.module.system.framework.member.MemberQualificationProperties.class})
@org.springframework.cloud.openfeign.EnableFeignClients(
        clients=cn.qinghe.mall.module.member.api.auth.MemberQualificationApi.class,
        defaultConfiguration=cn.qinghe.mall.module.system.framework.member.MemberQualificationFeignConfiguration.class)
@SpringBootApplication
public class SystemServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SystemServerApplication.class,args);
    }
}
