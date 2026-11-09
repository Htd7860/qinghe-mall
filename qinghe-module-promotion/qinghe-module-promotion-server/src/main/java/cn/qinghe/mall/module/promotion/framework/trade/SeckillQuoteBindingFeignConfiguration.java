package cn.qinghe.mall.module.promotion.framework.trade;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import feign.*;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用当前库存RPC边界写法，仅当前Feign子上下文生效。 */
public class SeckillQuoteBindingFeignConfiguration {
    @Bean public Request.Options bindingOptions() { return new Request.Options(1000,20000,false); }
    @Bean public Retryer bindingRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Logger.Level bindingLogger() { return Logger.Level.NONE; }
    @Bean public RequestInterceptor bindingTrace() {
        return template -> { template.removeHeader(TraceUtils.HEADER);String trace=TraceUtils.current();if (trace!=null) template.header(TraceUtils.HEADER,trace); };
    }
    @Bean public ErrorDecoder bindingErrorDecoder() { return (method,response)->exception0(503,"报价绑定结果待确认，请查询原申请"); }
}
