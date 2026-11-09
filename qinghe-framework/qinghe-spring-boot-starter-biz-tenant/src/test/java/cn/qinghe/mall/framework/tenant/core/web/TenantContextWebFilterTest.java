package cn.qinghe.mall.framework.tenant.core.web;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 请求生命周期测试，不证明客户端提供的租户编号具有授权效力。 */
class TenantContextWebFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TenantContextWebFilter filter;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        filter = new TenantContextWebFilter(objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static MockHttpServletRequest request(String tenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant-filter-test");
        if (tenantHeader != null) {
            request.addHeader("tenant-id", tenantHeader);
        }
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", " 101 ", "00101", "9223372036854775807"})
    void validHeaderShouldBeVisibleOnlyDuringRequest(String header) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request(header), new MockHttpServletResponse(), (req, res) -> {
            called.set(true);
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(Long.valueOf(header.trim()));
            assertThat(TenantContextHolder.isIgnore()).isFalse();
        });
        assertThat(called.get()).isTrue();
        assertEmptyContext();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " \t "})
    void missingOrBlankHeaderShouldContinueWithEmptyContext(String header) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(header), response, (req, res) -> {
            called.set(true);
            assertEmptyContext();
        });
        assertThat(called.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertEmptyContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "-1", "9223372036854775808", "+1", "101,202", "1e2"})
    void invalidHeaderShouldReturnSafeJson400WithoutCallingDownstream(String header) throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(header), response, (req, res) -> called.set(true));

        assertThat(called.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("code").asInt()).isEqualTo(400);
        assertThat(body.path("msg").asText()).isEqualTo("租户编号格式不正确");
        assertEmptyContext();
    }

    static Stream<Arguments> downstreamFailures() {
        return Stream.of(
                Arguments.of(new IllegalArgumentException("业务参数异常")),
                Arguments.of(new ServletException("Servlet异常")),
                Arguments.of(new IOException("下游IO异常")));
    }

    @ParameterizedTest
    @MethodSource("downstreamFailures")
    void downstreamFailureShouldPropagateWithoutBecomingHeaderError(Exception expected) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Exception actual = assertThrows(expected.getClass(), () ->
                filter.doFilter(request("101"), response, (req, res) -> {
                    assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(101L);
                    if (expected instanceof ServletException) {
                        throw (ServletException) expected;
                    }
                    if (expected instanceof IOException) {
                        throw (IOException) expected;
                    }
                    throw (RuntimeException) expected;
                }));
        assertThat(actual).isSameAs(expected);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertEmptyContext();
    }

    @Test
    void requestEntryShouldDiscardStaleTenantAndIgnoreState() throws Exception {
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        filter.doFilter(request(null), new MockHttpServletResponse(), (req, res) -> assertEmptyContext());
        assertEmptyContext();
    }

    @Test
    void sequentialRequestsOnSameThreadShouldNotShareContext() throws Exception {
        filter.doFilter(request("101"), new MockHttpServletResponse(), (req, res) ->
                assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(101L));
        assertEmptyContext();
        filter.doFilter(request(null), new MockHttpServletResponse(), (req, res) -> assertEmptyContext());
        filter.doFilter(request("202"), new MockHttpServletResponse(), (req, res) ->
                assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(202L));
        assertEmptyContext();
    }

    private static void assertEmptyContext() {
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }
}
