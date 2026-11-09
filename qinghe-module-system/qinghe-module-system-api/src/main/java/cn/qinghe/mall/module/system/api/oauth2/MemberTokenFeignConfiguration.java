package cn.qinghe.mall.module.system.api.oauth2;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import feign.RequestInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 只用于会员认证的显式Feign子上下文；不自动重试可能已提交的签发请求。 */
public class MemberTokenFeignConfiguration {
    @Bean public RequestInterceptor memberTokenTrace() {
        return template -> {
            String trace=TraceUtils.current();
            template.removeHeader(TraceUtils.HEADER);
            if(trace!=null)template.header(TraceUtils.HEADER,trace);
        };
    }

    @Bean public Request.Options memberTokenOptions() { return new Request.Options(1000,12000,false); }
    @Bean public Retryer memberTokenRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Logger.Level memberTokenLogging() { return Logger.Level.NONE; }
    @Bean public ErrorDecoder memberTokenErrors() {
        // HTTP层401表示服务凭据配置错误，会员本身失效由CommonResult code401表示。
        return (method,response) -> exception0(503,"会员认证服务暂不可用，结果未确认");
    }
    @Bean public Decoder memberTokenDecoder(ObjectMapper json) {
        return (response,type) -> {
            if (response.body()==null) { throw exception0(503,"会员认证服务返回为空"); }
            // 内部响应只有最小入口或身份/令牌结果；反序列化前限制响应体。
            try (InputStream in=response.body().asInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[1024];int count;
                while ((count=in.read(buffer))!=-1) {
                    if (bytes.size()+count>16384) { throw exception0(503,"会员认证响应超出限制"); }
                    bytes.write(buffer,0,count);
                }
                return json.readValue(bytes.toByteArray(),json.constructType(type));
            } catch (IOException invalid) {
                // 不保留可能包含Token的原始JSON错误和cause。
                throw exception0(503,"会员认证响应无法确认");
            }
        };
    }
}
