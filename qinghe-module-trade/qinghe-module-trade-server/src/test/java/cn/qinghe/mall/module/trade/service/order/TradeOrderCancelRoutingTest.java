package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import javax.servlet.http.HttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** DEC-077: cancellation must not send seckill orders to ordinary stock/coupon cleanup. */
class TradeOrderCancelRoutingTest {
    private final TradeMemberAccess access=mock(TradeMemberAccess.class);
    private final TradeOrderMapper orders=mock(TradeOrderMapper.class);
    private final TradeOrderCancelResourceService ordinary=mock(TradeOrderCancelResourceService.class);
    private final HttpServletRequest request=mock(HttpServletRequest.class);
    private final TradeOrderCancelService service=new TradeOrderCancelService(access,orders,mock(OrderResourceClient.class),
            mock(TradeOrderPaymentSyncService.class),ordinary,mock(PlatformTransactionManager.class));
    private TradeOrderDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(73000001L);
        LoginUser user=new LoginUser();user.setId(101L);user.setTenantId(73000001L);when(access.actor()).thenReturn(user);
        row=new TradeOrderDO();row.setId(2L);row.setTenantId(73000001L);row.setUserId(101L);row.setType(1);
        row.setStatus(40);row.setPayStatus(false);row.setCancelResourcesReleased(false);
        when(orders.owned(73000001L,101L,2L)).thenReturn(row);when(orders.paymentOrder(73000001L,2L)).thenReturn(row);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();}
    @Test void pendingSeckillCleanupReportsPendingWithoutOrdinaryWrites() {
        ServiceException error=assertThrows(ServiceException.class,()->service.cancelByMember(request,"seckill-alpha",2L));
        assertEquals(503,error.getCode());verifyNoInteractions(ordinary);
    }
    @Test void confirmedSeckillCleanupReturnsSuccessWithoutOrdinaryWrites() {
        row.setCancelResourcesReleased(true);
        assertTrue(service.cancelByMember(request,"seckill-alpha",2L));verifyNoInteractions(ordinary);
    }
    @Test void ordinaryCancellationKeepsExistingCleanup() {
        row.setType(0);
        assertTrue(service.cancelByMember(request,"seckill-alpha",2L));verify(ordinary).release(2L);
    }
}
