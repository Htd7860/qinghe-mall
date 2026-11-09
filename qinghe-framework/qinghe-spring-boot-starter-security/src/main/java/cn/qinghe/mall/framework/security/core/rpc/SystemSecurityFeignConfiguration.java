package cn.qinghe.mall.framework.security.core.rpc;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import feign.RequestInterceptor;

import cn.qinghe.mall.framework.security.config.RemoteSecurityProperties;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 仅在该Feign客户端的子上下文生效；不标记Configuration，避免全局扫描覆盖其他客户端。 */
public class SystemSecurityFeignConfiguration {
    @Bean public RequestInterceptor securityTrace() {
        return template -> {
            String trace=TraceUtils.current();
            template.removeHeader(TraceUtils.HEADER);
            if(trace!=null)template.header(TraceUtils.HEADER,trace);
        };
    }

    @Bean
    public Request.Options securityRequestOptions(RemoteSecurityProperties properties) {
        return new Request.Options(properties.getConnectTimeoutMillis(), properties.getReadTimeoutMillis(), false);
    }

    @Bean
    public Retryer securityRetryer() { return Retryer.NEVER_RETRY; }

    @Bean
    public Logger.Level securityLoggerLevel() { return Logger.Level.NONE; }

    @Bean
    public ErrorDecoder securityErrorDecoder() {
        return (method, response) -> exception0(response.status() == 401 ? 401 : 503,
                response.status() == 401 ? "访问令牌无效或已过期" : "认证服务暂不可用");
    }
}
