package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillInitialRuntimeRedisDAO;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 商家HTTP委托已有首次epoch的OPEN/DB就绪和停止协调，无自动启动，开放仍受运行门禁控制。 */
@Service
@RequiredArgsConstructor
public class SeckillRuntimeLifecycleService {
    private final SeckillPublishDecisionService decisions;
    private final SeckillInitialRuntimeRedisDAO runtime;

    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO ready(SeckillPublishReqDTO intent) {
        outside();SeckillRedisEpochDO original=decisions.getInitialEpoch(intent);
        if (original==null || !java.util.Arrays.asList(15,20).contains(original.getStatus())) throw exception0(409,"原epoch尚未准备运行库存或已停止");
        boolean observedOpen=false;
        try {
            runtime.open(original);observedOpen=true; // 同Redis时钟必须已越过固定初始化截止；不补建缺Key。
            SeckillRedisEpochDO confirmed=decisions.confirmInitialReady(intent,original.getInitToken(),original.getDefinitionDigest());
            if (!runtime.openMatches(confirmed)) throw exception0(503,"原运行库存失效，不能重填或当作就绪");
            SeckillRedisEpochDO current=decisions.getInitialEpoch(intent);
            if (current==null || !Integer.valueOf(20).equals(current.getStatus())
                    || !original.getInitToken().equals(current.getInitToken())) throw exception0(409,"原epoch已变化，请查询停止或就绪状态");
            return current;
        } catch (RuntimeException error) {
            if (observedOpen) {
                try {
                    SeckillRedisEpochDO current=decisions.getInitialEpoch(intent);
                    if (current!=null && Integer.valueOf(30).equals(current.getStatus())) runtime.close(current);
                } catch (RuntimeException closureError) { error.addSuppressed(closureError); }
            }
            throw error; // 不把DB已提交的READY/STOP说成事务回滚，也不自动重启原epoch。
        }
    }

    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO stop(SeckillPublishReqDTO intent,String reason) {
        outside();SeckillRedisEpochDO stopped=decisions.stopInitialEpoch(intent,reason); // 必须先提交DB停止。
        runtime.close(stopped);return stopped; // Redis失败则原DB停止仍有效；保留原号以再次关闭，不重放业务清算。
    }
    private void outside() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("运行阶段协调不能嵌套数据库事务");
    }
}
