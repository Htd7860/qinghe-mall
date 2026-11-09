package cn.qinghe.mall.module.system.framework.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** 后台登录失败限额；由system安全配置注册并在绑定配置时校验。 */
@Data
@Validated
@ConfigurationProperties(prefix = "qinghe.auth.login-failure")
public class LoginFailureLimitProperties {

    @Min(value = 1, message = "最大失败次数不能小于1")
    @Max(value = 100, message = "最大失败次数不能超过100")
    private int maxFailures = 5;

    @Min(value = 60, message = "登录失败窗口不能短于60秒")
    @Max(value = 86400, message = "登录失败窗口不能超过86400秒")
    private long windowSeconds = 900;

    @Min(value = 1, message = "本地主体容量不能小于1")
    @Max(value = 100000, message = "本地主体容量不能超过100000")
    private int maxLocalSubjects = 10000;
}
