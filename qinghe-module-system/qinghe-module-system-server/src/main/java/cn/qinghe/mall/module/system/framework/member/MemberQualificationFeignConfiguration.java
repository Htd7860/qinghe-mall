package cn.qinghe.mall.module.system.framework.member;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import feign.RequestInterceptor;
import feign.*;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 与既有安全RPC相同的有界超时/无重试/无重定向/无凭据日志，仅该客户端子上下文使用。 */
public class MemberQualificationFeignConfiguration {
    @Bean public RequestInterceptor memberQualificationTrace() {
        return template -> {
            String trace=TraceUtils.current();
            template.removeHeader(TraceUtils.HEADER);
            if(trace!=null)template.header(TraceUtils.HEADER,trace);
        };
    }

    @Bean public Request.Options memberOptions() { return new Request.Options(1000,3000,false); }
    @Bean public Retryer memberRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Logger.Level memberLogging() { return Logger.Level.NONE; }
    @Bean public ErrorDecoder memberErrors() {
        // HTTP认证错误表示服务配置失败；真实会员失效由HTTP200 CommonResult code401返回。
        return (method,response) -> exception0(503,"会员资格服务暂不可用");
    }
}
