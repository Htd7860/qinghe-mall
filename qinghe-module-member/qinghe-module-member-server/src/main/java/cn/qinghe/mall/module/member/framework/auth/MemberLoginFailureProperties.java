package cn.qinghe.mall.module.member.framework.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;

/** 沿用后台账号失败量参数；按真实tenant/mobile形成独立消费者主体。 */
@Data
@Validated
@ConfigurationProperties("qinghe.member.login-failure")
public class MemberLoginFailureProperties {
    @Min(1) @Max(100) private int maxFailures=5;
    @Min(60) @Max(86400) private long windowSeconds=900;
    @Min(1) @Max(100000) private int maxLocalSubjects=10000;
}
