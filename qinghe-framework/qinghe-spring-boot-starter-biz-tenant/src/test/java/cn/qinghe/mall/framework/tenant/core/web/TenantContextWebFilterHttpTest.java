package cn.qinghe.mall.framework.tenant.core.web;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.config.QingheTenantWebAutoConfiguration;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.servlet.FilterRegistration;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实本机随机端口测试，验证FilterRegistrationBean进入Servlet容器。
 * 只导入Web所需配置；不加载system应用、数据源、MyBatis或Nacos。
 */
class TenantContextWebFilterHttpTest {

    private static AnnotationConfigServletWebServerApplicationContext context;
    private static RestTemplate client;
    private static String endpoint;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void startIsolatedServletServer() {
        context = new AnnotationConfigServletWebServerApplicationContext();
        TestPropertyValues.of("server.port=0", "qinghe.tenant.enable=true")
                .applyTo(context);
        context.register(TestWebConfiguration.class);
        try {
            context.refresh();
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
        endpoint = "http://127.0.0.1:" + context.getWebServer().getPort()
                + "/__tenant_filter_test/context";
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        client = new RestTemplate(requestFactory);
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                // 保留400响应，供测试检查状态与统一返回体。
                return false;
            }
        });
    }

    @AfterAll
    static void stopServletServer() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void containerShouldRegisterExactlyOneTenantFilterAndExposeContextDuringHttpRequest() throws Exception {
        FilterRegistration registration = context.getServletContext()
                .getFilterRegistration("tenantContextWebFilter");
        assertThat(registration).isNotNull();
        assertThat(registration.getUrlPatternMappings()).contains("/*");
        long registeredCount = context.getServletContext().getFilterRegistrations().values().stream()
                .filter(item -> TenantContextWebFilter.class.getName().equals(item.getClassName()))
                .count();
        assertThat(registeredCount).isEqualTo(1L);

        ResponseEntity<String> withTenant = get("101");
        assertThat(withTenant.getStatusCodeValue()).isEqualTo(200);
        JsonNode tenantBody = JSON.readTree(withTenant.getBody());
        assertThat(tenantBody.path("code").asInt()).isZero();
        assertThat(tenantBody.path("data").asLong()).isEqualTo(101L);

        ResponseEntity<String> withoutTenant = get(null);
        assertThat(withoutTenant.getStatusCodeValue()).isEqualTo(200);
        assertThat(JSON.readTree(withoutTenant.getBody()).path("data").isNull()).isTrue();
    }

    @Test
    void realHttpInvalidHeaderShouldReturnJson400() throws Exception {
        ResponseEntity<String> response = get("1.5");
        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().getSubtype()).isEqualTo("json");
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(400);
        assertThat(body.path("msg").asText()).isEqualTo("租户编号格式不正确");
    }

    private static ResponseEntity<String> get(String tenantHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (tenantHeader != null) {
            headers.set("tenant-id", tenantHeader);
        }
        return client.exchange(endpoint, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            QingheTenantWebAutoConfiguration.class
    })
    static class TestWebConfiguration {

        @Bean
        TomcatServletWebServerFactory webServerFactory() throws UnknownHostException {
            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
            factory.setAddress(InetAddress.getByName("127.0.0.1"));
            return factory;
        }

        @Bean
        ContextController contextController() {
            return new ContextController();
        }
    }

    @RestController
    static class ContextController {

        @GetMapping("/__tenant_filter_test/context")
        public CommonResult<Long> context() {
            return CommonResult.success(TenantContextHolder.getTenantId());
        }
    }
}
