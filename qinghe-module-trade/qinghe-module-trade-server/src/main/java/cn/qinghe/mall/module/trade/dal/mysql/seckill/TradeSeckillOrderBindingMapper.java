package cn.qinghe.mall.module.trade.dal.mysql.seckill;

import cn.qinghe.mall.module.trade.api.seckill.dto.SeckillQuoteBindingReqDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.*;
import org.apache.ibatis.annotations.*;

/** 成员父锁→原申请栅栏→原报价；状态30仅在本次未提交事务内占唯一键。 */
@Mapper
public interface TradeSeckillOrderBindingMapper {
    @Insert("INSERT INTO trade_seckill_order_binding(tenant_id,request_no,member_id,client_request_id,request_digest,quote_id,quote_digest,"
            + "state,fence_version,created_at,updated_at) VALUES(#{tenant},#{r.requestNo},#{r.memberId},#{r.clientRequestId},"
            + "#{r.requestDigest},#{r.quoteId},#{r.quoteDigest},30,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
    int ensure(@Param("tenant") Long tenant,@Param("r") SeckillQuoteBindingReqDTO request);

    @Select("SELECT * FROM trade_seckill_order_binding WHERE tenant_id=#{tenant} AND request_no=#{request} FOR UPDATE")
    TradeSeckillOrderBindingDO lock(@Param("tenant") Long tenant,@Param("request") String request);

    @Select("SELECT * FROM trade_seckill_order_binding WHERE tenant_id=#{tenant} AND request_no=#{request}")
    TradeSeckillOrderBindingDO get(@Param("tenant") Long tenant,@Param("request") String request);

    @Update("UPDATE trade_seckill_order_binding SET state=0,activity_id=#{q.activityId},generation=#{q.generation},"
            + "activity_version=#{q.activityVersion},redis_epoch=#{q.redisEpoch},sku_id=#{q.skuId},quantity=#{q.quantity},price_cent=#{q.priceCent},"
            + "goods_amount=#{q.goodsAmount},shipping_amount=#{q.shippingAmount},pay_amount=#{q.payAmount},quote_expires_at=#{q.expiresAt},"
            + "updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{q.tenantId} AND request_no=#{request} AND member_id=#{q.memberId} "
            + "AND quote_id=#{q.quoteId} AND quote_digest=#{q.quoteDigest} AND state=30 AND fence_version=0 AND order_id IS NULL")
    int bindQuote(@Param("request") String request,@Param("q") TradeSeckillQuoteDO quote);

    @Update("UPDATE trade_seckill_order_binding SET state=20,fence_version=fence_version+1,close_code='CANCELLED_BEFORE_ORDER',"
            + "closed_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND request_no=#{request} "
            + "AND state IN(0,30) AND order_id IS NULL AND fence_version<9007199254740991")
    int closeBeforeOrder(@Param("tenant") Long tenant,@Param("request") String request);
}
