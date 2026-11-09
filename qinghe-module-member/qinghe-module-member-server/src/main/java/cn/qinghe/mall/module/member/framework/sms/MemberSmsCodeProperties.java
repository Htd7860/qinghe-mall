package cn.qinghe.mall.module.member.framework.sms;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;
/** 原SmsCodeProperties频率/日额度/过期语义；当前验证码固定安全随机6位。 */
@Data
@Validated
@ConfigurationProperties(prefix="qinghe.member.sms-code")
public class MemberSmsCodeProperties {
    @Min(1) @Max(3600) private int sendFrequencySeconds=60;
    @Min(1) @Max(100) private int dailyMaximum=10;
    @Min(60) @Max(900) private int expireSeconds=300;
    @Min(1) @Max(10) private int maxAttempts=5;
    @Min(1) @Max(128) private int maxConcurrent=16;
}
