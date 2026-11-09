package cn.qinghe.mall.gateway.util;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** 网关自产错误统一写出；不拦截或重写已正常返回的下游响应。 */
public final class GatewayResponseUtils {

    private static final Logger log = LoggerFactory.getLogger(GatewayResponseUtils.class);
    private static final byte[] FALLBACK_BODY =
            "{\"code\":500,\"msg\":\"系统异常\",\"data\":null}".getBytes(StandardCharsets.UTF_8);

    private GatewayResponseUtils() {
    }

    public static Mono<Void> writeError(ServerWebExchange exchange, ObjectMapper objectMapper,
                                        HttpStatus status, String message) {
        return Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();
            if (response.isCommitted()) {
                return Mono.error(new IllegalStateException("响应已提交，无法改写错误结果"));
            }
            HttpStatus actualStatus = status;
            byte[] body;
            try {
                body = objectMapper.writeValueAsBytes(CommonResult.error(status.value(), message));
            } catch (JsonProcessingException ex) {
                log.error("网关错误响应序列化失败，返回固定500结果");
                actualStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                body = FALLBACK_BODY;
            }
            response.setStatusCode(actualStatus);
            HttpHeaders headers = response.getHeaders();
            // 错误JSON不能沿用尚未提交的上游压缩/长度或实体缓存元数据。
            headers.remove(HttpHeaders.CONTENT_ENCODING);
            headers.remove(HttpHeaders.TRANSFER_ENCODING);
            headers.remove(HttpHeaders.CONTENT_RANGE);
            headers.remove(HttpHeaders.ETAG);
            headers.remove(HttpHeaders.LAST_MODIFIED);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(body.length);
            headers.setCacheControl("no-store");
            if (actualStatus == HttpStatus.UNAUTHORIZED) {
                headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            } else {
                headers.remove(HttpHeaders.WWW_AUTHENTICATE);
            }
            // Retry-After等调用方已设置的协议头继续保留；HEAD只有状态和响应头。
            if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
                return response.setComplete();
            }
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        });
    }
}
