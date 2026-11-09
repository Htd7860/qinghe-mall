package cn.qinghe.mall.module.promotion.framework.product;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import feign.*;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取现有OrderResourceFeignConfiguration；库存写15秒事务，读取响应20秒，无自动重试/重定向/凭据日志。 */
public class SeckillStockFeignConfiguration {
    @Bean public Request.Options seckillStockOptions() { return new Request.Options(1000,20000,false); }
    @Bean public Retryer seckillStockRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Logger.Level seckillStockLogger() { return Logger.Level.NONE; }
    @Bean public RequestInterceptor seckillStockTrace() {
        return template -> {
            template.removeHeader(TraceUtils.HEADER);String trace=TraceUtils.current();
            if (trace!=null) template.header(TraceUtils.HEADER,trace);
        };
    }
    @Bean public ErrorDecoder seckillStockErrorDecoder() {
        return (method,response) -> {
            int code=Arrays.asList(400,401,403,404,409,429).contains(response.status())?response.status():503;
            return exception0(code,code==503?"秒杀库存结果待确认，请按原发布号查询":"秒杀库存命令被拒绝");
        };
    }
}
