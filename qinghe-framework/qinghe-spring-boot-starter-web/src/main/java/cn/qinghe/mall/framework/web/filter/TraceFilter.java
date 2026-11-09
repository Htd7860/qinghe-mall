package cn.qinghe.mall.framework.web.filter;
import cn.qinghe.mall.framework.common.util.trace.TraceUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** 择取原TraceFilter的响应头/继续链职责；无APM代理时显式建立有界日志编号。
 * @author 芋道源码
 */
@Slf4j
public class TraceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        clear();
        request.removeAttribute(TraceUtils.AUTHENTICATED_TENANT_ATTRIBUTE);
        request.removeAttribute(TraceUtils.AUTHENTICATED_ACTOR_ATTRIBUTE);
        List<String> headers=Collections.list(request.getHeaders(TraceUtils.HEADER));
        // 内部或直接请求的合法值仅作为不可信日志关联线索，从不用于身份授权。
        String trace=headers.size()==1 && TraceUtils.valid(headers.get(0))?headers.get(0):TraceUtils.create();
        MDC.put(TraceUtils.KEY,trace);request.setAttribute(TraceUtils.KEY,trace);response.setHeader(TraceUtils.HEADER,trace);
        long started=System.nanoTime();
        try {chain.doFilter(request,response);}
        finally {
            try {
                // 内层身份桥接已清MDC；只从它写入的服务器属性补本条结束日志，不读HTTP字段。
                Object tenant=request.getAttribute(TraceUtils.AUTHENTICATED_TENANT_ATTRIBUTE);
                Object actor=request.getAttribute(TraceUtils.AUTHENTICATED_ACTOR_ATTRIBUTE);
                if(tenant instanceof Long && (Long)tenant>=0 && actor instanceof Long && (Long)actor>0) {
                    MDC.put("tenantId",tenant.toString());MDC.put("actorId",actor.toString());
                }
                log.info("HTTP dispatch finished status={}, elapsedMs={}, async={}",response.getStatus(),
                        (System.nanoTime()-started)/1_000_000,request.isAsyncStarted());
            } finally {
                clear();request.removeAttribute(TraceUtils.AUTHENTICATED_TENANT_ATTRIBUTE);
                request.removeAttribute(TraceUtils.AUTHENTICATED_ACTOR_ATTRIBUTE);
            }
        }
    }
    private void clear(){MDC.remove(TraceUtils.KEY);MDC.remove("tenantId");MDC.remove("actorId");MDC.remove("orderId");MDC.remove("eventId");}
}
