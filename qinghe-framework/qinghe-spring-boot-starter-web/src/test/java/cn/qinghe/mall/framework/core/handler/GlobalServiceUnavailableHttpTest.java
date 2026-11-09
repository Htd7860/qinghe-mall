package cn.qinghe.mall.framework.core.handler;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.web.config.QingheWebExceptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** DEC-075 regression for the actual search entry failure; no database or external service. */
class GlobalServiceUnavailableHttpTest {
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new GlobalExceptionHandler(new QingheWebExceptionProperties())).build();

    @Test void temporaryFailureKeeps503WithFixedPublicMessage() throws Exception {
        mvc.perform(get("/entry-verification/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg").value("服务暂不可用，请稍后重试"))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void unexpectedFailureRemainsSanitized500() throws Exception {
        mvc.perform(get("/entry-verification/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("系统异常"));
    }
    @RestController static class Endpoint {
        @GetMapping("/entry-verification/unavailable") public void unavailable(){throw new ServiceException(503,"private upstream diagnostics");}
        @GetMapping("/entry-verification/failure") public void failure(){throw new ServiceException(500,"private upstream diagnostics");}
    }
}
