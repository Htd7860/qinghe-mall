package cn.qinghe.mall.framework.security.core.handler;

import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.core.handler
 * @Project：qinghe-mall
 * @name：AuthenticationEntryPointImpl
 * @Date：2026/10/22 19:36
 * @Filename：AuthenticationEntryPointImpl
 */
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPointImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");

        objectMapper.writeValue(
                response.getOutputStream(),
                CommonResult.error(GlobalErrorCodeConstants.UNAUTHORIZED)
        );
    }
}
