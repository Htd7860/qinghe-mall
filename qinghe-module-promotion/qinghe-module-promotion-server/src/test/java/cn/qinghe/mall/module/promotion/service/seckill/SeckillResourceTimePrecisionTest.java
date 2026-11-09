package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.trade.SeckillOrderResultClient;
import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillOrderStateDTO;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline repeat-confirmation branches; mapper/transaction mocks do not prove real atomicity. */
class SeckillResourceTimePrecisionTest {
    private final SeckillAdmissionMapper admissions=mock(SeckillAdmissionMapper.class);
    private final SeckillActivityMapper activities=mock(SeckillActivityMapper.class);
    private final SeckillOrderResultMapper results=mock(SeckillOrderResultMapper.class);
    private final SeckillOrderResourceMapper resources=mock(SeckillOrderResourceMapper.class);
    private final SeckillOrderResultClient trade=mock(SeckillOrderResultClient.class);
    private final SeckillOrderCacheSyncService cache=mock(SeckillOrderCacheSyncService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final SeckillOrderResourceService service=new SeckillOrderResourceService(admissions,activities,results,resources,trade,cache,manager);
    private final SeckillOrderStateDTO proof=new SeckillOrderStateDTO();
    private final LocalDateTime original=LocalDateTime.of(2026,11,1,9,29,3,67_000_000);
    private final String request="00000000-0000-4000-8000-000000000077";
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(73000001L);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        SeckillRequestDO row=new SeckillRequestDO();row.setTenantId(73000001L);row.setMemberId(42L);row.setRequestNo(request);
        row.setClientRequestId(request);row.setActivityId(1L);row.setGeneration(1);row.setSkuId(2L);row.setQuantity(1);
        row.setPriceCent(601);row.setGoodsAmount(601L);row.setShippingAmount(300L);row.setPayAmount(901L);
        row.setAcceptedTime(original.minusMinutes(1));row.setReconcileDeadline(row.getAcceptedTime().plusSeconds(120));
        row.setPayDeadline(row.getAcceptedTime().plusMinutes(30));row.setStatus(50);row.setOrderId(3L);row.setVersion(2L);
        row.setRequestDigest("a");row.setResourceState("CANCELLED");row.setResourcePayOrderId(4L);row.setResourceOrderVersion(5L);row.setResourceTime(original);
        when(admissions.forDispatch(73000001L,request)).thenReturn(row);when(results.lockRequest(73000001L,request)).thenReturn(row);
        SeckillActivityDO activity=new SeckillActivityDO();activity.setGeneration(1);when(activities.lock(73000001L,1L)).thenReturn(activity);
        when(admissions.lockPool(73000001L,1L,2L)).thenReturn(new SeckillActivitySkuDO());
        when(admissions.lockQuota(73000001L,1L,1,42L)).thenReturn(new SeckillMemberQuotaDO());
        when(resources.journalExists(row,"ORDER_CANCELLED",false)).thenReturn(1L);
        proof.setPayStatus(false);proof.setOrderStatus(40);proof.setPayOrderId(4L);proof.setOrderVersion(5L);when(trade.state(row)).thenReturn(proof);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();}
    @Test void subMillisecondTailDoesNotRepeatRelease() {sameMillisecond(56_000);}
    @Test void highSubMillisecondTailUsesTheSameExplicitPrecision() {sameMillisecond(987_000);}
    private void sameMillisecond(int tail) {
        proof.setCancelTime(original.plusNanos(tail).atOffset(ZoneOffset.UTC));
        assertTrue(service.synchronize(request));assertTrue(service.synchronize(request));
        verify(resources,never()).pool(any(),any(),anyBoolean());verify(resources,never()).member(any(),any(),anyBoolean());
        verify(resources,never()).complete(any(),anyString(),any(),any(),any());
    }
    @Test void differentMillisecondRemainsAConflict() {
        proof.setCancelTime(original.plusNanos(1_000_000).atOffset(ZoneOffset.UTC));
        assertThrows(IllegalStateException.class,()->service.synchronize(request));verifyNoInteractions(cache);
    }
}
