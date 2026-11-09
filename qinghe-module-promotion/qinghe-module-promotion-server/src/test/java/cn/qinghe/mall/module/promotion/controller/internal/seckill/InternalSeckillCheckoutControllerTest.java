package cn.qinghe.mall.module.promotion.controller.internal.seckill;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillCheckoutContextService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** DEC-077: domain refusals must reach the trade RPC caller as a CommonResult. */
class InternalSeckillCheckoutControllerTest {
    private final SeckillCheckoutContextService service=mock(SeckillCheckoutContextService.class);
    private final InternalSeckillCheckoutController controller=new InternalSeckillCheckoutController(service);

    @Test void quantityRefusalIsReadableByRpcCaller() { refusal(400); }
    @Test void unavailableGenerationIsReadableByRpcCaller() { refusal(409); }
    private void refusal(int code) {
        when(service.current(1L,1,2L,1)).thenThrow(exception0(code,"Domain refusal"));
        MockHttpServletResponse response=new MockHttpServletResponse();
        CommonResult<SeckillCheckoutContextDTO> result=controller.current(1L,1,2L,1,response);
        assertEquals(200,response.getStatus());assertEquals(code,result.getCode());assertNull(result.getData());
        assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test void dependencyFailureStillPropagates() {
        ServiceException failure=exception0(503,"Dependency unavailable");
        when(service.current(1L,1,2L,1)).thenThrow(failure);
        assertSame(failure,assertThrows(ServiceException.class,()->controller.current(1L,1,2L,1,new MockHttpServletResponse())));
    }
    @Test void successfulSnapshotIsPreserved() {
        SeckillCheckoutContextDTO expected=new SeckillCheckoutContextDTO();
        when(service.current(1L,1,2L,1)).thenReturn(expected);
        CommonResult<SeckillCheckoutContextDTO> result=controller.current(1L,1,2L,1,new MockHttpServletResponse());
        assertEquals(0,result.getCode());assertSame(expected,result.getData());
    }
}
