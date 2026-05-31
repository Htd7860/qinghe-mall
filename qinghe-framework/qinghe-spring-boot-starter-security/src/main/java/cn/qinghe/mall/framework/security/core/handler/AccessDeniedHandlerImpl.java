package cn.qinghe.mall.framework.security.core.handler;

import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.core.handler
 * @Project：qinghe-mall
 * @name：AccessDeniedHandlerImpl
 * @Date：2026/10/22 20:04
 * @Filename：AccessDeniedHandlerImpl
 */
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    private ObjectMapper objectMapper;

    public AccessDeniedHandlerImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");

        objectMapper.writeValue(
                response.getOutputStream(),
                CommonResult.error(GlobalErrorCodeConstants.FORBIDDEN)
        );
    }
}
