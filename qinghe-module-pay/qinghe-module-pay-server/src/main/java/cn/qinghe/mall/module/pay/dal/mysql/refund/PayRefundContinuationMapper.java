package cn.qinghe.mall.module.pay.dal.mysql.refund;
import cn.qinghe.mall.module.pay.dal.dataobject.refund.PayRefundContinuationDO;
import org.apache.ibatis.annotations.*;

/** 写入与原退款预算CAS共享原支付父锁、同一事务。 */
@Mapper
public interface PayRefundContinuationMapper {
    @Select("SELECT * FROM pay_refund_continuation WHERE tenant_id=#{tenant} AND request_id=#{request}")
    PayRefundContinuationDO get(@Param("tenant") Long tenant,@Param("request") String request);
    @Select("SELECT COUNT(*) FROM pay_refund_continuation WHERE tenant_id=#{tenant} AND refund_id=#{id}")
    long count(@Param("tenant") Long tenant,@Param("id") Long id);
    @Insert("INSERT INTO pay_refund_continuation(tenant_id,refund_id,request_id,actor_id,reason,from_version,from_limit,to_limit,create_time) "
        +"VALUES(#{row.tenantId},#{row.refundId},#{row.requestId},#{row.actorId},#{row.reason},#{row.fromVersion},#{row.fromLimit},#{row.toLimit},#{row.createTime})")
    int insert(@Param("row") PayRefundContinuationDO row);
}
