package cn.qinghe.mall.framework.job.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;

/** 择取原XxlJobProperties，保留字段职责；本项目默认关闭且启用须显式配置凭据。
 * @author 芋道源码
 */
@Data
@Validated
@ConfigurationProperties("xxl.job")
public class XxlJobProperties {
    private boolean enabled=false;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43,128}") @ToString.Exclude
    private String accessToken;
    @NotNull @Valid private AdminProperties admin;
    @NotNull @Valid private ExecutorProperties executor;
    @Data
    public static class AdminProperties {
        @NotBlank private String addresses;
    }
    @Data
    public static class ExecutorProperties {
        @NotBlank @Pattern(regexp="[a-z0-9-]{3,64}") private String appName;
        private String ip;
        @NotNull @Min(1024) @Max(65535) private Integer port=18083;
        @NotBlank private String logPath;
        @NotNull @Min(1) @Max(365) private Integer logRetentionDays=30;
    }
}
