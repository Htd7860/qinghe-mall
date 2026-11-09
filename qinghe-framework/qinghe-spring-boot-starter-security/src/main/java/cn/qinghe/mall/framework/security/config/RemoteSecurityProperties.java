package cn.qinghe.mall.framework.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/** 独立Servlet业务服务的system认证RPC；system本身继续使用进程内实现。 */
@Data
@Validated
@ConfigurationProperties(prefix = "qinghe.security.remote")
public class RemoteSecurityProperties {
    @NotBlank
    private String systemBaseUrl = "http://127.0.0.1:8080";
    @Min(100) @Max(10000)
    private int connectTimeoutMillis = 1000;
    @Min(100) @Max(30000)
    private int readTimeoutMillis = 3000;
    @Min(1) @Max(1000)
    private int maxConcurrentCalls = 32;
}
