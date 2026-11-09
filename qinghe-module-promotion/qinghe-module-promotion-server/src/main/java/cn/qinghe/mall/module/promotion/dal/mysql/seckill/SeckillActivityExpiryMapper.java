package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 原停止职责的系统到期调用；DB UTC决定到期，关闭缓存未知保留原义务。 */
@Mapper
public interface SeckillActivityExpiryMapper {
    String DUE_FROM=" FROM promotion_seckill_activity a LEFT JOIN promotion_seckill_redis_epoch e "
            + "ON e.tenant_id=a.tenant_id AND e.activity_id=a.id AND e.generation=a.generation AND e.epoch=1 "
            + "WHERE a.deleted=b'0' AND ((a.status=20 AND UTC_TIMESTAMP(3)>=a.end_time "
            + "AND ((a.redis_epoch=0 AND e.epoch IS NULL) OR (a.redis_epoch=1 AND e.status IN(0,10,15,20)))) "
            + "OR (a.status=30 AND a.redis_epoch=1 AND e.status=30 AND e.stop_cache_state='PENDING'))";

    @Select("<script>SELECT a.*"+DUE_FROM+" AND a.tenant_id=#{tenant} "
            + "<if test='after != null'>AND a.id &gt; #{after}</if> ORDER BY a.id LIMIT #{limit}</script>")
    List<SeckillActivityDO> candidates(@Param("tenant") Long tenant,@Param("after") Long after,@Param("limit") int limit);

    @Update("UPDATE promotion_seckill_redis_epoch SET stop_cache_state='SYNCED' WHERE tenant_id=#{e.tenantId} AND activity_id=#{e.activityId} "
            + "AND generation=#{e.generation} AND epoch=#{e.epoch} AND publish_request_id=#{e.publishRequestId} "
            + "AND status=30 AND stop_cache_state='PENDING' AND init_token=#{e.initToken} AND definition_digest=#{e.definitionDigest}")
    int cacheSynced(@Param("e") SeckillRedisEpochDO original);

    @Insert("INSERT INTO promotion_seckill_redis_epoch(tenant_id,activity_id,generation,epoch,publish_request_id,actor_id,from_version,to_version,"
            + "definition_snapshot,definition_digest,status,stop_cache_state,create_time) VALUES(#{e.tenantId},#{e.activityId},#{e.generation},1,"
            + "#{e.publishRequestId},#{e.actorId},#{e.fromVersion},#{e.toVersion},CAST(#{e.definitionSnapshot} AS JSON),#{e.definitionDigest},40,'NOT_REQUIRED',UTC_TIMESTAMP(3))")
    int insertColdStopped(@Param("e") SeckillRedisEpochDO original);

    @Update("UPDATE promotion_seckill_redis_epoch SET status=40 WHERE tenant_id=#{e.tenantId} AND activity_id=#{e.activityId} "
            + "AND generation=#{e.generation} AND epoch=1 AND publish_request_id=#{e.publishRequestId} AND status IN(0,10) "
            + "AND init_token IS NULL AND init_deadline IS NULL AND stop_cache_state='NOT_REQUIRED' AND definition_digest=#{e.definitionDigest}")
    int closeBeforeRuntime(@Param("e") SeckillRedisEpochDO original);

    @Update("UPDATE promotion_seckill_activity a JOIN promotion_seckill_redis_epoch e ON e.tenant_id=a.tenant_id AND e.activity_id=a.id "
            + "AND e.generation=a.generation AND e.epoch=1 AND e.publish_request_id=a.publish_operation_no "
            + "SET a.status=30,a.redis_epoch=1,a.stop_reason='活动到期，运行初始化前关闭',a.stopped_by='SYSTEM:EXPIRY',a.updater='SYSTEM:EXPIRY',"
            + "a.version=a.version+1,a.update_time=UTC_TIMESTAMP(3) WHERE a.tenant_id=#{tenant} AND a.id=#{activity} AND a.version=#{version} "
            + "AND a.version<9007199254740991 AND a.status=20 AND a.redis_epoch IN(0,1) AND a.end_time<=UTC_TIMESTAMP(3) AND a.deleted=b'0' "
            + "AND e.status=40 AND e.init_token IS NULL AND e.init_deadline IS NULL")
    int stopColdActivity(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("version") Long version);

    @Update("UPDATE promotion_seckill_activity a JOIN promotion_seckill_redis_epoch e ON e.tenant_id=a.tenant_id AND e.activity_id=a.id "
            + "AND e.generation=a.generation AND e.epoch=1 AND e.publish_request_id=a.publish_operation_no "
            + "SET a.status=30,a.redis_epoch=1,a.stop_reason=#{reason},a.stopped_by=#{actor},a.updater=#{actor},a.version=a.version+1,a.update_time=UTC_TIMESTAMP(3) "
            + "WHERE a.tenant_id=#{tenant} AND a.id=#{activity} AND a.version=#{version} AND a.version<9007199254740991 "
            + "AND a.status=20 AND a.redis_epoch IN(0,1) AND a.deleted=b'0' AND e.status=40 AND e.init_token IS NULL AND e.init_deadline IS NULL")
    int stopColdByPlatform(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("version") Long version,
            @Param("actor") String actor,@Param("reason") String reason);

    @Select("SELECT to_version FROM promotion_seckill_activity_audit WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND action='EPOCH_STOP' AND actor_id=#{actor} AND from_version=#{version} AND to_version=#{version}+1 "
            + "AND JSON_EXTRACT(details,'$.generation')=#{generation} AND JSON_EXTRACT(details,'$.actorTenantId')=0 "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.actorRealm'))='platform' AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.source'))='PLATFORM' "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.reason'))=#{reason} LIMIT 1")
    Long platformStopVersion(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("version") Long version,@Param("actor") Long actor,@Param("reason") String reason);

    @Select("SELECT (EXISTS(SELECT 1 FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation})) "
            + "+(EXISTS(SELECT 1 FROM promotion_seckill_admission_intent WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation})) "
            + "+(EXISTS(SELECT 1 FROM promotion_seckill_outbox o JOIN promotion_seckill_request q ON q.tenant_id=o.tenant_id AND q.request_no=o.request_no "
            + "WHERE q.tenant_id=#{tenant} AND q.activity_id=#{activity} AND q.generation=#{generation})) "
            + "+(EXISTS(SELECT 1 FROM promotion_seckill_member_quota WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} "
            + "AND (held_quantity>0 OR paid_quantity>0)))")
    Long coldObligations(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);
}
