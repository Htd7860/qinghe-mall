package cn.qinghe.mall.framework.security.core.handler;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;

/** MVC方法安全异常先于通用Exception处理，过滤器内异常仍由安全链处理。 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<Object>> accessDenied(
            AccessDeniedException ex, HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(CommonResult.error(403, "没有该操作权限"));
    }
}
