package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillPublishedManifestRedisDAO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.time.Duration;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 商家HTTP委托首次epoch暂停seed物化；不创建运行库存或READY，无任务/启动调用。 */
@Service
@RequiredArgsConstructor
public class SeckillEpochMaterializationService {
    private final SeckillPublishDecisionService decisions;
    private final SeckillPublishedManifestRedisDAO redis;

    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO materialize(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("暂停物化入口不能嵌套数据库事务");
        SeckillRedisEpochDO original=decisions.prepareInitialEpoch(intent);
        if (Integer.valueOf(15).equals(original.getStatus())) throw exception0(409,"已领取原运行初始化许可，请查询原窗口，不能返回seed阶段");
        SeckillPublishedManifest definition=JsonUtils.parseObject(original.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        if (definition==null || definition.getEndAt()==null || definition.getEndAt()<=Instant.now().toEpochMilli())
            throw exception0(409,"原活动已结束或定义不完整");
        if (Integer.valueOf(0).equals(original.getStatus())) {
            long expires=Math.min(definition.getEndAt(),Instant.now().plus(Duration.ofHours(24)).toEpochMilli());
            redis.installPausedSeed(original,expires);
        }
        // 原DB已经确认过seed则只MATCH，缺Key绝不自动按初始余额重新写入。
        if (!redis.pausedSeedMatches(original)) throw exception0(503,"原暂停seed缺失，需要受控恢复，不能重新初始化");
        SeckillRedisEpochDO confirmed=decisions.confirmInitialSeed(intent,original.getDefinitionDigest());
        if (!redis.pausedSeedMatches(confirmed)) throw exception0(503,"暂停seed确认后失效，请核查原epoch");
        return confirmed; // status10仅说明曾确认过暂停seed，不代表可接单。
    }
}
