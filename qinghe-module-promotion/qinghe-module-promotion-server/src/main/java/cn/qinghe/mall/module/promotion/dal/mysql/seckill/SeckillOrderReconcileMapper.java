package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 仅原已建单申请的资源终局核对元数据，不重置业务版本/受理次数或创建新订单。 */
@Mapper
public interface SeckillOrderReconcileMapper {
    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND order_id IS NOT NULL AND resource_state='HELD' "
            + "AND status IN(20,30,60) AND resource_check_attempts<10 AND next_resource_check_at<=UTC_TIMESTAMP(3) "
            + "ORDER BY next_resource_check_at,request_no LIMIT #{limit}")
    List<SeckillRequestDO> due(@Param("tenant") Long tenant,@Param("limit") int limit);

    @Update("UPDATE promotion_seckill_request SET next_resource_check_at=TIMESTAMPADD(SECOND,"
            + "CASE WHEN #{failed} THEN LEAST(900,15*POW(2,LEAST(resource_check_attempts,6))) ELSE 15 END,UTC_TIMESTAMP(3)),"
            + "resource_check_attempts=resource_check_attempts+CASE WHEN #{failed} THEN 1 ELSE 0 END,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} AND order_id=#{r.orderId} AND resource_state='HELD' "
            + "AND version=#{r.version} AND status IN(20,30,60) AND resource_check_attempts=#{r.resourceCheckAttempts} "
            + "AND next_resource_check_at=#{r.nextResourceCheckAt} AND resource_check_attempts<10")
    int defer(@Param("r") SeckillRequestDO original,@Param("failed") boolean failed);

    @Select("SELECT COUNT(*) FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND order_id IS NOT NULL "
            + "AND resource_state='HELD' AND resource_check_attempts>=10")
    long manual(@Param("tenant") Long tenant);
}
