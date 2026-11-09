package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillAdmissionIntentDO;
import org.apache.ibatis.annotations.*;

/** 原请求栅栏只前进，不删除/重置；受理10必须与原申请/额度/事件在同一事务写入。 */
@Mapper
public interface SeckillAdmissionIntentMapper {
    @Select("SELECT * FROM promotion_seckill_admission_intent WHERE tenant_id=#{tenant} AND member_id=#{member} AND client_request_id=#{client}")
    SeckillAdmissionIntentDO get(@Param("tenant") Long tenant,@Param("member") Long member,@Param("client") String client);
    @Select("SELECT * FROM promotion_seckill_admission_intent WHERE tenant_id=#{tenant} AND request_no=#{request} FOR UPDATE")
    SeckillAdmissionIntentDO lock(@Param("tenant") Long tenant,@Param("request") String request);
    @Insert("INSERT INTO promotion_seckill_admission_intent(tenant_id,request_no,member_id,client_request_id,request_digest,activity_id,generation,sku_id,"
            + "request_snapshot,context_digest,status,failure_code,version,created_at,updated_at,quote_expires_at,cache_sync_state,recovery_attempts,next_recovery_at) VALUES(#{r.tenantId},#{r.requestNo},#{r.memberId},"
            + "#{r.clientRequestId},#{r.requestDigest},#{r.activityId},#{r.generation},#{r.skuId},#{r.requestSnapshot},#{r.contextDigest},0,NULL,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),#{r.quoteExpiresAt},'PENDING',0,UTC_TIMESTAMP(3))")
    int insert(@Param("r") SeckillAdmissionIntentDO row);
    @Update("UPDATE promotion_seckill_admission_intent SET status=20,failure_code=#{code},version=version+1,updated_at=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND request_no=#{request} AND status=0 AND version=#{version} AND version<9007199254740991")
    int reject(@Param("tenant") Long tenant,@Param("request") String request,@Param("version") Long version,@Param("code") Integer code);
    @Update("UPDATE promotion_seckill_admission_intent SET status=10,version=version+1,updated_at=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND request_no=#{request} AND status=0 AND version=#{version} AND version<9007199254740991")
    int accepted(@Param("tenant") Long tenant,@Param("request") String request,@Param("version") Long version);
}
