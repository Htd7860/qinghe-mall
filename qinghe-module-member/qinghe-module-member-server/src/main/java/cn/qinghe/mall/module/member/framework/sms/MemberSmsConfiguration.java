package cn.qinghe.mall.module.member.framework.sms;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** DISABLED默认失败关闭；ALIYUN显式配置；测试收件箱必须显式profile且仅回环绑定。 */
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(MemberSmsProperties.class)
public class MemberSmsConfiguration {
    @Bean
    public MemberSmsSender memberSmsSender(MemberSmsProperties properties,Environment environment) {
        String mode=properties.getMode();
        if ("DISABLED".equals(mode)) { return (logId,tenantId,mobile,scene,code) -> { throw exception0(503,"短信渠道尚未启用"); }; }
        if ("ALIYUN".equals(mode)) { return new AliyunMemberSmsSender(properties); }
        if ("TEST".equals(mode)) {
            if (!environment.acceptsProfiles(Profiles.of("sms-test")) || environment.acceptsProfiles(Profiles.of("prod","production"))
                    || !Arrays.asList("127.0.0.1","localhost","::1").contains(environment.getProperty("server.address"))) {
                throw new IllegalArgumentException("测试短信只允许sms-test profile且显式回环绑定，不能在生产profile使用");
            }
            return new TestMemberSmsOutbox();
        }
        throw new IllegalArgumentException("短信渠道模式不正确");
    }
}
