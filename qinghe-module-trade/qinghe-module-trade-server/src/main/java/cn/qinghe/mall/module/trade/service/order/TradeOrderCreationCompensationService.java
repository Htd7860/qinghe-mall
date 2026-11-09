package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderPrepareMapper;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原TradeOrderUpdateServiceImpl取消时退库存/退券职责，补跨服务结果未知的恢复顺序。
 * 仅供可信创建流程/补偿任务内部调用，尚无会员/后台HTTP入口。
 * PREPARED/PROCESSING不能有可付款主订单；以后正式建单必须锁同一准备行并原子写CREATED。
 * 已CREATED订单必须走支付安全关单，绝不能调用此方法。
 * @author 芋道源码
 */
@Service
@Slf4j
public class TradeOrderCreationCompensationService {
    private final TradeOrderPrepareMapper preparations;
    private final OrderResourceClient resources;
    private final TransactionTemplate write;

    public TradeOrderCreationCompensationService(TradeOrderPrepareMapper preparations,OrderResourceClient resources,
            PlatformTransactionManager manager) {
        this.preparations=preparations;this.resources=resources;
        write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
    }

    /** 超时不宣称回滚，也不由调用者提供user/coupon/数量；恢复时仍用同一preparationId。 */
    public void compensate(Long preparationId) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("创建补偿必须在外层事务之外执行");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || preparationId==null || preparationId<=0 || preparationId>9_007_199_254_740_991L)throw exception0(403,"创建补偿需要可信租户和准备编号");
        String stage=write.execute(status -> {
            TradeOrderPrepareDO row=required(tenant,preparationId);
            if(Arrays.asList("COMPENSATING","FAILED").contains(row.getStage()))return row.getStage();
            if(!Arrays.asList("PREPARED","PROCESSING").contains(row.getStage()))throw exception0(409,"该订单不能按创建失败回补");
            transition(row,"COMPENSATING");return "COMPENSATING";
        });
        if("FAILED".equals(stage))return;
        if(!"COMPENSATING".equals(stage))throw new IllegalStateException("创建补偿未持久关闭准备记录");

        // 即使一侧故障，也尝试关闭另一侧；写超时留COMPENSATING，重试返回各自永久回执。
        boolean confirmed=true;
        try {resources.closeCreationStock(preparationId);} catch(RuntimeException error) {
            confirmed=false;log.warn("创建失败库存补偿待确认 tenant={}, preparation={}",tenant,preparationId);
        }
        try {resources.closeCreationCoupon(preparationId);} catch(RuntimeException error) {
            confirmed=false;log.warn("创建失败优惠券补偿待确认 tenant={}, preparation={}",tenant,preparationId);
        }
        if(!confirmed)throw exception0(503,"订单创建已停止，资源补偿待确认，请按原准备编号恢复");

        write.execute(status -> {
            TradeOrderPrepareDO row=required(tenant,preparationId);
            if("FAILED".equals(row.getStage()))return null;
            if(!"COMPENSATING".equals(row.getStage()))throw exception0(409,"订单补偿阶段已变化");
            transition(row,"FAILED");return null;
        });
    }

    private TradeOrderPrepareDO required(Long tenant,Long id) {
        TradeOrderPrepareDO row=preparations.lockProgress(tenant,id);
        if(row==null)throw exception0(404,"本租户订单准备记录不存在");
        return row;
    }
    private void transition(TradeOrderPrepareDO row,String stage) {
        if(preparations.compensateStage(row.getTenantId(),row.getId(),row.getStage(),stage,row.getAuthVersion())!=1)
            throw exception0(409,"订单准备阶段或版本已变化");
    }
}
