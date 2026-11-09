package cn.qinghe.mall.framework.core.handler;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.ServiceExceptionUtil;
import cn.qinghe.mall.framework.web.config.QingheWebExceptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.*;

/**
 * Servlet MVC exception boundary. Spring owns framework exception dispatch;
 * this class supplies the CommonResult contract and application error policy.
 * Authentication filters and the reactive Gateway have separate boundaries.
 */
@RestControllerAdvice
@Order(0)
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Set<Integer> ignoredBusinessErrorCodes;

    public GlobalExceptionHandler(QingheWebExceptionProperties properties) {
        Set<Integer> configuredCodes = properties.getIgnoredBusinessErrorCodes();
        this.ignoredBusinessErrorCodes = configuredCodes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(configuredCodes));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Object> serviceExceptionHandler(ServiceException ex, WebRequest request) {
        Integer code = ex.getCode();
        // Expected temporary unavailability must retain 503 for callers to distinguish it from a bug.
        // Keep a fixed public message; exception details and upstream diagnostics are not exposed.
        if (Integer.valueOf(503).equals(code)) {
            String location = "unknown";
            for (StackTraceElement frame : ex.getStackTrace()) {
                if (frame.getClassName().startsWith("cn.qinghe.mall.")
                        && !ServiceExceptionUtil.class.getName().equals(frame.getClassName())) {
                    location = frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                    break;
                }
            }
            log.warn("Service temporarily unavailable; location={}, traceId={}", location, MDC.get("traceId"));
            return errorResponse(ex, 503, "服务暂不可用，请稍后重试",
                    new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
        }
        // A malformed business exception must never turn into success or expose a system message.
        if (code == null || code <= 0 || (code >= 500 && code < 600) || UNKNOWN.getCode().equals(code)) {
            return systemFailure(ex, request);
        }
        if (!ignoredBusinessErrorCodes.contains(code)) {
            // Do not log business messages: they may contain user data.
            log.warn("Business request rejected; code={}, traceId={}", code, MDC.get("traceId"));
        }
        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "业务处理失败";
        return errorResponse(ex, code, message, new HttpHeaders(), businessStatus(code), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> constraintViolationExceptionHandler(
            ConstraintViolationException ex, WebRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(StringUtils::hasText)
                .findFirst().orElse(BAD_REQUEST.getMsg());
        return invalidParameters(ex, message, new HttpHeaders(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> maxUploadSizeExceededExceptionHandler(
            MaxUploadSizeExceededException ex, WebRequest request) {
        return errorResponse(ex, 413, "上传文件过大", new HttpHeaders(), HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> responseStatusExceptionHandler(ResponseStatusException ex, WebRequest request) {
        HttpStatus status = ex.getStatus();
        if (!status.isError()) {
            return systemFailure(ex, request);
        }
        if (status.is5xxServerError()) {
            logUnexpected(ex);
        }
        // The exception's reason is internal diagnostic information, not a public error message.
        return errorResponse(ex, status.value(), publicMessage(status), new HttpHeaders(), status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> defaultExceptionHandler(Exception ex, WebRequest request) {
        Exception actual = unwrapInvocationException(ex);
        if (actual instanceof ServiceException) {
            return serviceExceptionHandler((ServiceException) actual, request);
        }
        if (actual instanceof ConstraintViolationException) {
            return constraintViolationExceptionHandler((ConstraintViolationException) actual, request);
        }
        if (actual instanceof ResponseStatusException) {
            return responseStatusExceptionHandler((ResponseStatusException) actual, request);
        }
        return systemFailure(ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        return invalidParameters(ex, validationMessage(ex.getBindingResult()), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleBindException(BindException ex,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        return invalidParameters(ex, validationMessage(ex.getBindingResult()), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        if (status.is5xxServerError()) {
            logUnexpected(ex);
        } else {
            log.warn("Invalid HTTP request; status={}, type={}, traceId={}",
                    status.value(), ex.getClass().getSimpleName(), MDC.get("traceId"));
        }
        // Preserve Spring's status and protocol headers (Allow, Accept, etc.).
        return errorResponse(ex, status.value(), publicMessage(status), headers, status, request);
    }

    private ResponseEntity<Object> invalidParameters(Exception ex, String message,
            HttpHeaders headers, WebRequest request) {
        log.warn("Request validation failed; type={}, traceId={}",
                ex.getClass().getSimpleName(), MDC.get("traceId"));
        return errorResponse(ex, BAD_REQUEST.getCode(), message, headers, HttpStatus.BAD_REQUEST, request);
    }

    private ResponseEntity<Object> systemFailure(Exception ex, WebRequest request) {
        logUnexpected(ex);
        return errorResponse(ex, INTERNAL_SERVER_ERROR.getCode(), INTERNAL_SERVER_ERROR.getMsg(),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private void logUnexpected(Exception ex) {
        // Stack traces stay in server logs; never append request bodies, query strings or credentials.
        log.error("Unhandled request failure; traceId={}", MDC.get("traceId"), ex);
    }

    @Nullable
    private ResponseEntity<Object> errorResponse(Exception ex, int code, String message,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        if (request instanceof ServletWebRequest) {
            HttpServletResponse response = ((ServletWebRequest) request).getResponse();
            if (response != null && response.isCommitted()) {
                return null;
            }
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        responseHeaders.setCacheControl("no-store");
        return super.handleExceptionInternal(ex, CommonResult.error(code, message), responseHeaders, status, request);
    }

    private static String validationMessage(BindingResult result) {
        // Type-conversion default messages can contain the rejected value and nested exception text.
        if (result.getFieldErrors().stream().anyMatch(error -> error.isBindingFailure())) {
            return BAD_REQUEST.getMsg();
        }
        // Object-level validation errors are valid too; a field error need not exist.
        return result.getAllErrors().stream().map(ObjectError::getDefaultMessage)
                .filter(StringUtils::hasText).findFirst().orElse(BAD_REQUEST.getMsg());
    }

    private static HttpStatus businessStatus(int code) {
        if (code == REPEATED_REQUESTS.getCode()) {
            return HttpStatus.CONFLICT;
        }
        if (code == DEMO_DENY.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code >= 400 && code < 500) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        // Ordinary domain rejection retains yudao's HTTP 200 + nonzero business code contract.
        return HttpStatus.OK;
    }

    private static String publicMessage(HttpStatus status) {
        if (status.is5xxServerError()) {
            return INTERNAL_SERVER_ERROR.getMsg();
        }
        switch (status) {
            case BAD_REQUEST: return BAD_REQUEST.getMsg();
            case UNAUTHORIZED: return UNAUTHORIZED.getMsg();
            case FORBIDDEN: return FORBIDDEN.getMsg();
            case NOT_FOUND: return NOT_FOUND.getMsg();
            case METHOD_NOT_ALLOWED: return METHOD_NOT_ALLOWED.getMsg();
            case NOT_ACCEPTABLE: return "不支持请求的响应格式";
            case CONFLICT: return "请求与当前资源状态冲突";
            case PAYLOAD_TOO_LARGE: return "上传文件过大";
            case UNSUPPORTED_MEDIA_TYPE: return "不支持请求的数据格式";
            case TOO_MANY_REQUESTS: return TOO_MANY_REQUESTS.getMsg();
            default: return "请求处理失败";
        }
    }

    private static Exception unwrapInvocationException(Exception ex) {
        Exception current = ex;
        // Only unwrap infrastructure wrappers; arbitrary causes do not redefine an error's meaning.
        for (int depth = 0; depth < 16; depth++) {
            boolean wrapper = current instanceof CompletionException || current instanceof ExecutionException
                    || current instanceof InvocationTargetException || current instanceof UndeclaredThrowableException;
            Throwable cause = current.getCause();
            if (!wrapper || !(cause instanceof Exception) || cause == current) {
                break;
            }
            current = (Exception) cause;
        }
        return current;
    }
}
