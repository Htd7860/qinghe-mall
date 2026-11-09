package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillPublishedManifestRedisDAO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.time.Instant;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 仅预载不可变发布定义；无HTTP/任务入口，不初始化可变库存、epoch或消费者准入。 */
@Service
@RequiredArgsConstructor
public class SeckillManifestPreloadService {
    private final SeckillPublishDecisionService decisions;
    private final SeckillPublishedManifestRedisDAO cache;

    /** 返回本次观察到的定义摘要，不是活动/Redis准入已就绪的凭证。 */
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public String preload(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("预载入口不能嵌套数据库事务");
        SeckillPublishedManifest expected=decisions.publishedDefinition(intent);
        long expiry=Math.min(expected.getEndAt(),Instant.now().plus(Duration.ofHours(24)).toEpochMilli());
        String fingerprint=cache.install(expected,expiry); // 数据库只读事务已经结束，不持锁访问Redis。
        SeckillPublishedManifest current=decisions.publishedDefinition(intent);
        if (!JsonUtils.toJsonString(expected).equals(JsonUtils.toJsonString(current))) throw exception0(409,"原发布已变化，不能继续预载");
        if (!cache.matches(current)) throw exception0(503,"发布定义缓存已失效，请按原请求重查");
        return fingerprint;
    }
}
