package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillInitialRuntimeRedisDAO;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 商家HTTP委托原固定初始化许可→Redis暂停hash→原凭证复查；不自动执行READY或恢复。 */
@Service
@RequiredArgsConstructor
public class SeckillInitialRuntimeService {
    private final SeckillPublishDecisionService decisions;
    private final SeckillInitialRuntimeRedisDAO runtime;

    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO initialize(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("运行初始化不能嵌套数据库事务");
        SeckillRedisEpochDO original=decisions.claimInitialRuntime(intent);
        runtime.installPaused(original);
        SeckillRedisEpochDO current=decisions.claimInitialRuntime(intent); // 重复查询不换token、不续期。
        if (!original.getInitToken().equals(current.getInitToken()) || !original.getInitDeadline().equals(current.getInitDeadline())
                || !original.getDefinitionDigest().equals(current.getDefinitionDigest()) || !runtime.pausedMatches(current))
            throw exception0(503,"暂停运行库存尚未确认，请核查原epoch");
        return current; // 状态15仍不开放准入，后续OPEN必须在Redis时钟超过initDeadline后执行。
    }
}
