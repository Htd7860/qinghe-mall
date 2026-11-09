package cn.qinghe.mall.gateway.filter;

import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import java.util.Map;

/** 择取原TraceFilter响应关联编号职责；WebFlux编号放exchange/Context，不跨信号持有MDC。
 * @author 芋道源码
 */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class GatewayTraceFilter implements WebFilter {
    private static final Logger log=LoggerFactory.getLogger(GatewayTraceFilter.class);
    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
        return Mono.defer(() -> {
            String trace=TraceUtils.create();long started=System.nanoTime();
            // 公网输入不决定本次编号；路由与认证WebClient都传递同一个服务端编号。
            exchange.getAttributes().put(TraceUtils.KEY,trace);
            ServerWebExchange forwarded=exchange.mutate().request(builder -> builder.headers(headers ->
                    headers.set(TraceUtils.HEADER,trace))).build();
            exchange.getResponse().getHeaders().set(TraceUtils.HEADER,trace);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(TraceUtils.HEADER,trace);return Mono.empty();
            });
            return chain.filter(forwarded).doFinally(signal -> {
                // 仅在这次同步日志调用内设置MDC，然后恢复该线程原上下文。
                Map<String,String> previous=MDC.getCopyOfContextMap();
                try {
                    MDC.clear();MDC.put(TraceUtils.KEY,trace);
                    log.info("Gateway dispatch terminated elapsedMs={}, signal={}",(System.nanoTime()-started)/1_000_000,signal);
                } finally {if(previous==null)MDC.clear();else MDC.setContextMap(previous);}
            }).contextWrite(context -> context.put(TraceUtils.KEY,trace));
        });
    }
}
