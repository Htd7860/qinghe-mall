package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import org.apache.ibatis.annotations.*;

/** 原活动条件更新职责；先锁活动父行，再创建一次性epoch凭证，Redis访问不在此事务。 */
@Mapper
public interface SeckillRedisEpochMapper {
    @Select("SELECT * FROM promotion_seckill_redis_epoch WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} AND epoch=1")
    SeckillRedisEpochDO initial(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Insert("INSERT INTO promotion_seckill_redis_epoch(tenant_id,activity_id,generation,epoch,publish_request_id,actor_id,"
            + "from_version,to_version,definition_snapshot,definition_digest,status,create_time) "
            + "VALUES(#{row.tenantId},#{row.activityId},#{row.generation},1,#{row.publishRequestId},#{row.actorId},"
            + "#{row.fromVersion},#{row.toVersion},CAST(#{row.definitionSnapshot} AS JSON),#{row.definitionDigest},0,UTC_TIMESTAMP(3))")
    int insertInitial(@Param("row") SeckillRedisEpochDO row);

    @Update("UPDATE promotion_seckill_activity SET redis_epoch=1,version=version+1,updater=#{actor},update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} AND publish_operation_no=#{request} "
            + "AND status=20 AND redis_epoch=0 AND version=#{expected} AND version<9007199254740991 "
            + "AND end_time>UTC_TIMESTAMP(3) AND deleted=b'0'")
    int beginInitial(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor);

    @Update("UPDATE promotion_seckill_redis_epoch SET status=10 WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND generation=#{generation} AND epoch=1 AND publish_request_id=#{request} AND definition_digest=#{digest} AND status=0")
    int markSeed(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("digest") String digest);

    @Update("UPDATE promotion_seckill_activity SET version=version+1,updater=#{actor},update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} AND publish_operation_no=#{request} "
            + "AND status=20 AND redis_epoch=1 AND version=#{expected} AND version<9007199254740991 AND end_time>UTC_TIMESTAMP(3) AND deleted=b'0'")
    int advanceStage(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor);

    @Update("UPDATE promotion_seckill_redis_epoch e JOIN promotion_seckill_activity a "
            + "ON a.tenant_id=e.tenant_id AND a.id=e.activity_id AND a.generation=e.generation "
            + "SET e.status=15,e.init_token=#{token},e.init_deadline=LEAST(a.end_time,TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(3))) "
            + "WHERE e.tenant_id=#{tenant} AND e.activity_id=#{activity} AND e.generation=#{generation} AND e.epoch=1 "
            + "AND e.publish_request_id=#{request} AND e.status=10 AND e.init_token IS NULL AND e.init_deadline IS NULL "
            + "AND a.status=20 AND a.redis_epoch=1 AND a.publish_operation_no=#{request} AND a.version=#{expected} "
            + "AND a.deleted=b'0' AND a.end_time>UTC_TIMESTAMP(3)")
    int claimRuntime(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("token") String token);

    @Update("UPDATE promotion_seckill_redis_epoch SET status=20 WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} "
            + "AND epoch=1 AND publish_request_id=#{request} AND init_token=#{token} AND definition_digest=#{digest} "
            + "AND status=15 AND init_deadline<=UTC_TIMESTAMP(3)")
    int markReady(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("token") String token,@Param("digest") String digest);

    @Update("UPDATE promotion_seckill_redis_epoch SET status=30,stop_cache_state='PENDING' WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} "
            + "AND epoch=1 AND publish_request_id=#{request} AND status IN(15,20)")
    int markStopping(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("request") String request);

    @Update("UPDATE promotion_seckill_activity SET status=30,stop_reason=#{reason},stopped_by=#{actor},updater=#{actor}, "
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} "
            + "AND publish_operation_no=#{request} AND redis_epoch=1 AND status=20 AND version=#{expected} "
            + "AND version<9007199254740991 AND deleted=b'0'")
    int stopActivity(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor,@Param("reason") String reason);
}
