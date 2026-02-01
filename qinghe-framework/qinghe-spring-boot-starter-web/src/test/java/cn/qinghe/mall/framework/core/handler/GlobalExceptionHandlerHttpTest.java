package cn.qinghe.mall.framework.core.handler;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.web.config.QingheWebExceptionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs through MVC and actual starter discovery, without importing the advice directly. */
@SpringBootTest(classes = GlobalExceptionHandlerHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false",
        "qinghe.web.exception.ignored-business-error-codes[0]=1001001002"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private GlobalExceptionHandler handler;

    @Test
    void successfulResponseRemainsUnchanged() throws Exception {
        mvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.checkedData").doesNotExist());
    }

    @Test
    void businessRejectionPreservesCodeAndMessage() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001001001))
                .andExpect(jsonPath("$.msg").value("库存不足"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @ParameterizedTest
    @CsvSource({"400,400", "401,401", "403,403", "404,404", "405,405", "423,423", "429,429",
            "900,409", "901,403"})
    void reservedCodesHaveAnExplicitHttpStatus(int code, int httpStatus) throws Exception {
        mvc.perform(get("/test/business").param("code", String.valueOf(code)))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 500, 502, 999})
    void invalidOrSystemBusinessCodesCannotProduceSuccess(int code) throws Exception {
        mvc.perform(get("/test/business").param("code", String.valueOf(code)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("系统异常"));
    }

    @Test
    void nullBusinessCodeIsSafe() {
        ResponseEntity<Object> result = handler.serviceExceptionHandler(
                new ServiceException(null, "internal detail"), request());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((CommonResult<?>) result.getBody()).getMsg()).isEqualTo("系统异常");
    }

    @Test
    void nestedInvocationWrappersPreserveBusinessFailure() throws Exception {
        mvc.perform(get("/test/wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001001001))
                .andExpect(jsonPath("$.msg").value("库存不足"));
    }

    @Test
    void unknownFailureDoesNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("系统异常"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("private_table"))));
    }

    @Test
    void missingRequestParameterIsBadRequest() throws Exception {
        mvc.perform(get("/test/typed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectedInputIsNotEchoedOrLogged(CapturedOutput output) throws Exception {
        mvc.perform(get("/test/typed").param("id", "PASSWORD_SECRET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("请求参数不正确"));
        assertThat(output.getAll()).doesNotContain("PASSWORD_SECRET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"name\":\"ok\",\"quantity\":\"PASSWORD_SECRET\"}"})
    void invalidJsonIsBadRequest(String body) throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数不正确"));
    }

    @Test
    void missingJsonBodyIsBadRequest() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void requestBodyValidationReturnsDefinedConstraintMessage() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("商品名称不能为空"));
    }

    @Test
    void pageValidationActuallyRuns() throws Exception {
        mvc.perform(get("/test/page").param("pageSize", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("每页条数最大值为 200"));
    }

    @Test
    void formBindingFailureDoesNotExposeTheRejectedValue(CapturedOutput output) throws Exception {
        mvc.perform(get("/test/page").param("pageNo", "PASSWORD_SECRET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("请求参数不正确"));
        assertThat(output.getAll()).doesNotContain("PASSWORD_SECRET");
    }

    @Test
    void objectLevelValidationDoesNotRequireAFieldError() {
        BindException ex = new BindException(new Object(), "criteria");
        ex.reject("invalid", "组合条件不正确");
        ResponseEntity<Object> result = handler.handleBindException(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request());
        assertThat(((CommonResult<?>) result.getBody()).getMsg()).isEqualTo("组合条件不正确");
    }

    @Test
    void emptyConstraintViolationSetDoesNotCauseAnotherException() throws Exception {
        mvc.perform(get("/test/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("请求参数不正确"));
    }

    @Test
    void validatorConfigurationFailureRemainsAServerFailure() throws Exception {
        mvc.perform(get("/test/validation-configuration"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("系统异常"));
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(post("/test/typed"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void unsupportedContentTypePreservesAcceptHeader() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString("application/json")))
                .andExpect(jsonPath("$.code").value(415));
    }

    @Test
    void unsupportedResponseFormatUsesTheErrorEnvelope() throws Exception {
        mvc.perform(get("/test/json").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value(406));
    }

    @Test
    void missingRouteUsesTheErrorEnvelopeWhenMvcIsConfiguredToThrow() throws Exception {
        mvc.perform(get("/missing-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void oversizedUploadReturns413() throws Exception {
        mvc.perform(get("/test/upload"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(413));
    }

    @Test
    void explicitHttpExceptionDoesNotLeakItsReason() throws Exception {
        mvc.perform(get("/test/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("账号未登录"));
    }

    @Test
    void configuredNoiseSuppressionDoesNotSuppressTheResponse(CapturedOutput output) throws Exception {
        mvc.perform(get("/test/business").param("code", "1001001002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001001002));
        assertThat(output.getAll()).doesNotContain("Business request rejected; code=1001001002");
    }

    @Test
    void committedResponseIsNotWrittenAgain() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        assertThat(handler.defaultExceptionHandler(new IllegalStateException("test"),
                new ServletWebRequest(new MockHttpServletRequest(), response))).isNull();
    }

    @Test
    void configuredCodesAreCopiedInsteadOfRetainingMutableConfiguration(CapturedOutput output) {
        QingheWebExceptionProperties properties = new QingheWebExceptionProperties();
        properties.getIgnoredBusinessErrorCodes().add(1001001003);
        GlobalExceptionHandler isolated = new GlobalExceptionHandler(properties);
        properties.getIgnoredBusinessErrorCodes().clear();
        isolated.serviceExceptionHandler(new ServiceException(1001001003, "库存不足"), request());
        assertThat(output.getAll()).doesNotContain("Business request rejected; code=1001001003");
    }

    private static ServletWebRequest request() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {
        @GetMapping("/success")
        public CommonResult<String> success() {
            return CommonResult.success("ok");
        }

        @GetMapping("/business")
        public CommonResult<?> business(@RequestParam(value = "code", defaultValue = "1001001001") int code) {
            throw new ServiceException(code, "库存不足");
        }

        @GetMapping("/wrapped")
        public CommonResult<?> wrapped() {
            throw new CompletionException(new ExecutionException(new ServiceException(1001001001, "库存不足")));
        }

        @GetMapping("/unexpected")
        public CommonResult<?> unexpected() {
            throw new IllegalStateException("Table 'private_table' doesn't exist; secret=internal");
        }

        @GetMapping("/typed")
        public CommonResult<Long> typed(@RequestParam("id") Long id) {
            return CommonResult.success(id);
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public CommonResult<String> body(@Valid @RequestBody ProductInput input) {
            return CommonResult.success(input.getName());
        }

        @GetMapping("/page")
        public CommonResult<PageParam> page(@Valid @ModelAttribute PageParam page) {
            return CommonResult.success(page);
        }

        @GetMapping("/constraint")
        public CommonResult<?> constraint() {
            throw new ConstraintViolationException(Collections.emptySet());
        }

        @GetMapping("/validation-configuration")
        public CommonResult<?> validationConfiguration() {
            throw new ValidationException("internal validator configuration");
        }

        @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        public CommonResult<String> json() {
            return CommonResult.success("ok");
        }

        @GetMapping("/upload")
        public CommonResult<?> upload() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/status")
        public CommonResult<?> status() {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "private diagnostic");
        }
    }

    public static class ProductInput {
        @NotBlank(message = "商品名称不能为空")
        private String name;
        private Integer quantity;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
