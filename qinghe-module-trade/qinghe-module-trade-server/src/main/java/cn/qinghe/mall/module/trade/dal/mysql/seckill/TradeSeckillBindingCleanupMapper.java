package cn.qinghe.mall.module.trade.dal.mysql.seckill;

import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillOrderBindingDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 只关闭超过原报价全部可能付款期限且尚未建单的绑定；原号与墓碑永久保留。 */
@Mapper
public interface TradeSeckillBindingCleanupMapper {
    @Select("SELECT * FROM trade_seckill_order_binding WHERE tenant_id=#{tenant} AND state=0 AND order_id IS NULL "
            + "AND cleanup_attempts<10 AND next_cleanup_at<=UTC_TIMESTAMP(3) "
            + "AND quote_expires_at<=TIMESTAMPADD(MINUTE,-30,UTC_TIMESTAMP(3)) ORDER BY next_cleanup_at,quote_expires_at,request_no LIMIT #{limit}")
    List<TradeSeckillOrderBindingDO> due(@Param("tenant") Long tenant,@Param("limit") int limit);

    @Update("UPDATE trade_seckill_order_binding SET state=20,fence_version=fence_version+1,close_code='CANCELLED_BEFORE_ORDER',"
            + "closed_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} "
            + "AND member_id=#{r.memberId} AND state=0 AND order_id IS NULL AND fence_version=#{r.fenceVersion} AND fence_version<9007199254740991 "
            + "AND quote_expires_at=#{r.quoteExpiresAt} AND quote_expires_at<=TIMESTAMPADD(MINUTE,-30,UTC_TIMESTAMP(3)) "
            + "AND NOT EXISTS(SELECT 1 FROM trade_order_prepare p WHERE p.tenant_id=#{r.tenantId} AND p.user_id=#{r.memberId} "
            + "AND p.source_type=1 AND p.request_id=#{r.requestNo})")
    int closeExpired(@Param("r") TradeSeckillOrderBindingDO original);

    @Update("UPDATE trade_seckill_order_binding SET next_cleanup_at=TIMESTAMPADD(SECOND,LEAST(900,15*POW(2,LEAST(cleanup_attempts,6))),UTC_TIMESTAMP(3)),"
            + "cleanup_attempts=cleanup_attempts+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND request_no=#{r.requestNo} "
            + "AND state=0 AND order_id IS NULL AND fence_version=#{r.fenceVersion} AND cleanup_attempts<10")
    int defer(@Param("r") TradeSeckillOrderBindingDO original);

    @Select("SELECT COUNT(*) FROM trade_seckill_order_binding WHERE tenant_id=#{tenant} AND state=0 AND order_id IS NULL "
            + "AND cleanup_attempts>=10 AND quote_expires_at<=TIMESTAMPADD(MINUTE,-30,UTC_TIMESTAMP(3))")
    long manual(@Param("tenant") Long tenant);
}
