package cn.qinghe.mall.framework.tenant.core.web;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.core.util.WebFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.core.web
 * @Project：qinghe-mall
 * @name：TenantContextWebFilter
 * @Date：2026/10/22 16:22
 * @Filename：TenantContextWebFilter
 */
public class TenantContextWebFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public TenantContextWebFilter(ObjectMapper objectMapper){
        this.objectMapper=objectMapper;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            TenantContextHolder.clear();
            Long tenantId;

            try {
                tenantId= WebFrameworkUtils.getTenantId(request);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json");

                objectMapper.writeValue(
                        response.getOutputStream(),
                        CommonResult.error(
                                BAD_REQUEST.getCode(),
                                "租户编号格式不正确"
                        ));
                        return ;
            }
            if(tenantId!=null){
                TenantContextHolder.setTenantId(tenantId);
            }
            filterChain.doFilter(request,response);
        } finally {
            TenantContextHolder.clear();
        }


    }
}
