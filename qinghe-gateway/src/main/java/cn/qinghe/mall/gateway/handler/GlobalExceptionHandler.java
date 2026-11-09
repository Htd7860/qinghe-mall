package cn.qinghe.mall.gateway.handler;

import cn.qinghe.mall.gateway.util.GatewayResponseUtils;
import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** WebFlux全局异常处理；Security已处理的拒绝和system的正常HTTP响应不会进入这里。 */
@Component
@Order(-2) // Boot默认JSON错误处理器为-1，必须先于它处理。
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        if (ex instanceof ResponseStatusException) {
            HttpHeaders protocolHeaders = ((ResponseStatusException) ex).getResponseHeaders();
            for (String name : Arrays.asList(HttpHeaders.ALLOW, HttpHeaders.ACCEPT, HttpHeaders.RETRY_AFTER)) {
                List<String> values = protocolHeaders.get(name);
                if (values != null) {
                    exchange.getResponse().getHeaders().put(name, values);
                }
            }
        }
        if (status.is5xxServerError()) {
            // 不输出URI/查询参数/异常原文或cause，避免泄露凭据和内部地址。
            log.error("网关请求失败，status={}，errorType={}，requestId={}，traceId={}",
                    status.value(), ex.getClass().getName(), exchange.getRequest().getId(),exchange.getAttribute(TraceUtils.KEY));
        }
        return GatewayResponseUtils.writeError(exchange, objectMapper, status, errorMessage(status));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof ResponseStatusException) {
                HttpStatus status = HttpStatus.resolve(((ResponseStatusException) current).getRawStatusCode());
                return status != null && status.isError() ? status : HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (current instanceof AuthenticationServiceException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (current instanceof AuthenticationException) {
                return HttpStatus.UNAUTHORIZED;
            }
            if (current instanceof AccessDeniedException) {
                return HttpStatus.FORBIDDEN;
            }
            // ConnectTimeoutException继承ConnectException，必须先判断超时。
            if (current instanceof TimeoutException || current instanceof ReadTimeoutException
                    || current instanceof ConnectTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (current instanceof ConnectException || current instanceof UnknownHostException
                    || current instanceof PrematureCloseException) {
                return HttpStatus.BAD_GATEWAY;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String errorMessage(HttpStatus status) {
        switch (status) {
            case BAD_REQUEST: return "请求参数不正确";
            case UNAUTHORIZED: return "账号未登录或访问令牌无效";
            case FORBIDDEN: return "没有该操作权限";
            case NOT_FOUND: return "请求未找到";
            case METHOD_NOT_ALLOWED: return "请求方法不正确";
            case NOT_ACCEPTABLE: return "不支持该响应格式";
            case PAYLOAD_TOO_LARGE: return "请求内容过大";
            case UNSUPPORTED_MEDIA_TYPE: return "不支持该请求格式";
            case TOO_MANY_REQUESTS: return "请求过于频繁，请稍后重试";
            case BAD_GATEWAY: return "下游服务连接异常，请稍后重试";
            case SERVICE_UNAVAILABLE: return "服务暂不可用，请稍后重试";
            case GATEWAY_TIMEOUT: return "服务响应超时，请稍后查询处理结果";
            default: return status.is4xxClientError() ? "请求无法处理" : "系统异常";
        }
    }
}
