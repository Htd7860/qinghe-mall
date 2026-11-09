package cn.qinghe.mall.module.trade.framework.order;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import feign.RequestInterceptor;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 只用于订单资源客户端：无自动重试/重定向/凭据日志，写超时由持久流程查询原结果。 */
public class OrderResourceFeignConfiguration {
    @Bean public RequestInterceptor orderResourceTrace() {
        return template -> {
            String trace=TraceUtils.current();
            template.removeHeader(TraceUtils.HEADER);
            if(trace!=null)template.header(TraceUtils.HEADER,trace);
        };
    }

    @Bean public Request.Options orderResourceOptions() { return new Request.Options(1000,20000,false); }
    @Bean public Retryer orderResourceRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Logger.Level orderResourceLogger() { return Logger.Level.NONE; }
    @Bean public ErrorDecoder orderResourceErrorDecoder() {
        return (method,response) -> {
            int code=Arrays.asList(400,401,403,404,409,429).contains(response.status())?response.status():503;
            return exception0(code,code==503?"订单资源结果待确认，请按原订单查询":"订单资源请求被拒绝");
        };
    }
}
