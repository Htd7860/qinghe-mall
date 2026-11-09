package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillAdmissionIntentDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 原意图的缓存进度，不改变已提交的受理库存/额度，不恢复已耗尽的预算。 */
@Mapper
public interface SeckillIntentRecoveryMapper {
    @Select("SELECT * FROM promotion_seckill_admission_intent WHERE tenant_id=#{tenant} AND cache_sync_state='PENDING' "
            + "AND recovery_attempts<10 AND next_recovery_at<=UTC_TIMESTAMP(3) AND (status IN(10,20) OR (status=0 AND quote_expires_at<=UTC_TIMESTAMP(3))) "
            + "ORDER BY next_recovery_at,request_no LIMIT #{limit}")
    List<SeckillAdmissionIntentDO> due(@Param("tenant") Long tenant,@Param("limit") Integer limit);

    @Update("UPDATE promotion_seckill_admission_intent SET status=20,failure_code=409,version=version+1,updated_at=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND status=0 AND version=#{r.version} "
            + "AND quote_expires_at<=UTC_TIMESTAMP(3) AND NOT EXISTS(SELECT 1 FROM promotion_seckill_request x WHERE x.tenant_id=#{r.tenantId} AND x.request_no=#{r.requestNo})")
    int rejectExpired(@Param("r") SeckillAdmissionIntentDO original);

    @Update("UPDATE promotion_seckill_admission_intent SET cache_sync_state='SYNCED',cache_synced_at=UTC_TIMESTAMP(3),"
            + "version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} "
            + "AND status=#{r.status} AND status IN(10,20) AND version=#{r.version} AND version<9007199254740991 AND cache_sync_state='PENDING'")
    int synced(@Param("r") SeckillAdmissionIntentDO original);

    @Update("UPDATE promotion_seckill_admission_intent SET recovery_error_code=503,"
            + "next_recovery_at=TIMESTAMPADD(SECOND,LEAST(900,15*POW(2,LEAST(recovery_attempts,6))),UTC_TIMESTAMP(3)),"
            + "recovery_attempts=recovery_attempts+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} "
            + "AND version=#{r.version} AND cache_sync_state='PENDING' AND recovery_attempts<10")
    int defer(@Param("r") SeckillAdmissionIntentDO original);

    @Select("SELECT COUNT(*) FROM promotion_seckill_admission_intent WHERE tenant_id=#{tenant} AND cache_sync_state='PENDING' AND recovery_attempts>=10")
    long manual(@Param("tenant") Long tenant);

    @Select("SELECT event_id FROM promotion_seckill_order_result WHERE tenant_id=#{tenant} AND request_no=#{request} "
            + "AND event_type='SECKILL_REQUEST_CLOSED' AND result_state=20 AND order_id IS NULL ORDER BY processed_at,event_id LIMIT 1")
    String closedEvent(@Param("tenant") Long tenant,@Param("request") String request);
}
