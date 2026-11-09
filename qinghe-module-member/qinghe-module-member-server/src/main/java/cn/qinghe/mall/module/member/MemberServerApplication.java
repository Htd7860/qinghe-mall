package cn.qinghe.mall.module.member;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import cn.qinghe.mall.module.member.framework.address.MemberAddressProperties;
/** 独立会员Servlet实例；消费者认证使用精确公开接口，服务间资格入口单独认证。 */
@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({MemberAddressProperties.class,
        cn.qinghe.mall.module.member.framework.sms.MemberSmsCodeProperties.class,
        cn.qinghe.mall.module.member.framework.auth.MemberLoginFailureProperties.class,
        cn.qinghe.mall.module.member.framework.auth.MemberTokenClientProperties.class})
@org.springframework.cloud.openfeign.EnableFeignClients(
        clients={cn.qinghe.mall.module.system.api.oauth2.MemberTokenIssueApi.class,
                cn.qinghe.mall.module.system.api.tenant.MemberStorefrontApi.class})
public class MemberServerApplication {
    public static void main(String[] args) { SpringApplication.run(MemberServerApplication.class,args); }
}
