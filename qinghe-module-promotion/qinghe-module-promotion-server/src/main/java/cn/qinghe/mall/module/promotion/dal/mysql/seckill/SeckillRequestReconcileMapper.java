package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 复用原申请状态/next_retry_time/retry_count，不另建超时流程或跨库事务。 */
@Mapper
public interface SeckillRequestReconcileMapper {
    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND order_id IS NULL AND status IN(10,60) "
            + "AND reconcile_deadline<=UTC_TIMESTAMP(3) AND next_retry_time<=UTC_TIMESTAMP(3) AND retry_count<10 "
            + "AND (locked_until IS NULL OR locked_until<=UTC_TIMESTAMP(3)) ORDER BY next_retry_time,request_no LIMIT #{limit}")
    List<SeckillRequestDO> due(@Param("tenant") Long tenant,@Param("limit") Integer limit);

    @Update("UPDATE promotion_seckill_request SET status=30,version=version+1,fence_version=fence_version+1,"
            + "failure_code=409,claim_token=NULL,locked_until=NULL,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND version=#{r.version} AND version<9007199254740991 "
            + "AND fence_version<9007199254740991 AND status IN(10,60) AND order_id IS NULL AND retry_count<10 "
            + "AND reconcile_deadline<=UTC_TIMESTAMP(3) AND (locked_until IS NULL OR locked_until<=UTC_TIMESTAMP(3))")
    int startClose(@Param("r") SeckillRequestDO request);

    @Insert("INSERT INTO promotion_seckill_outbox(tenant_id,event_id,request_no,event_type,aggregate_version,schema_version,payload,status,"
            + "attempts,available_at,replay_version,created_at) VALUES(#{r.tenantId},#{r.eventId},#{r.requestNo},'SECKILL_CLOSE',"
            + "#{r.aggregateVersion},1,CAST(#{r.payload} AS JSON),'PENDING',0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3))")
    int insertClose(@Param("r") SeckillOutboxDO event);

    /** 独立失败进度，不改库存/个人额度；到10次只进入未知待核查，不冒称已关闭。 */
    @Update("UPDATE promotion_seckill_request SET status=CASE WHEN retry_count=9 THEN 60 ELSE status END,"
            + "version=version+CASE WHEN retry_count=9 THEN 1 ELSE 0 END,"
            + "next_retry_time=TIMESTAMPADD(SECOND,LEAST(900,15*POW(2,LEAST(retry_count,6))),UTC_TIMESTAMP(3)),"
            + "retry_count=retry_count+1,failure_code=503,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND version=#{r.version} AND version<9007199254740991 "
            + "AND status IN(10,60) AND order_id IS NULL AND retry_count<10 AND reconcile_deadline<=UTC_TIMESTAMP(3) "
            + "AND (locked_until IS NULL OR locked_until<=UTC_TIMESTAMP(3))")
    int defer(@Param("r") SeckillRequestDO original);

    @Select("SELECT COUNT(*) FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND status IN(10,60) AND order_id IS NULL "
            + "AND retry_count>=10 AND reconcile_deadline<=UTC_TIMESTAMP(3)")
    long manual(@Param("tenant") Long tenant);
}
