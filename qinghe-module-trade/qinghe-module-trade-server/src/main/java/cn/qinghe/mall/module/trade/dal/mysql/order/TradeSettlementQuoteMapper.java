package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeSettlementQuoteDO;
import org.apache.ibatis.annotations.*;
/** 固定本人查询/成功报价；绑定只由持久订单准备事务调用，不接浏览器订单编号。 */
@Mapper
public interface TradeSettlementQuoteMapper {
    @Select("SELECT id,tenant_id,user_id,quote_id,request_id,request_digest,cart_version,address_id,coupon_id,snapshot,created_at,expires_at,bound_order_id "
        + "FROM trade_settlement_quote WHERE tenant_id=#{tenant} AND user_id=#{user} AND request_id=#{request}")
    TradeSettlementQuoteDO findRequest(@Param("tenant") Long tenant,@Param("user") Long user,@Param("request") String request);
    @Select("SELECT * FROM trade_settlement_quote WHERE tenant_id=#{tenant} AND user_id=#{user} AND quote_id=#{quote}")
    TradeSettlementQuoteDO findQuote(@Param("tenant") Long tenant,@Param("user") Long user,@Param("quote") String quote);
    @Select("SELECT * FROM trade_settlement_quote WHERE tenant_id=#{tenant} AND user_id=#{user} AND quote_id=#{quote} FOR UPDATE")
    TradeSettlementQuoteDO lockQuote(@Param("tenant") Long tenant,@Param("user") Long user,@Param("quote") String quote);
    /** 未登记准备的显式终止；调用方持有购物车父锁及报价锁，不缩短已登记订单的原期限。 */
    @Update("UPDATE trade_settlement_quote SET snapshot=NULL WHERE tenant_id=#{tenant} AND user_id=#{user} "
        + "AND quote_id=#{quote} AND bound_order_id IS NULL AND snapshot IS NOT NULL")
    int closeUnprepared(@Param("tenant") Long tenant,@Param("user") Long user,@Param("quote") String quote);
    @Update("UPDATE trade_settlement_quote SET bound_order_id=#{order} WHERE tenant_id=#{tenant} AND user_id=#{user} "
            + "AND quote_id=#{quote} AND bound_order_id IS NULL AND snapshot IS NOT NULL AND expires_at>#{now}")
    int bind(@Param("tenant") Long tenant,@Param("user") Long user,@Param("quote") String quote,
            @Param("order") Long order,@Param("now") java.time.LocalDateTime now);
    @Select("SELECT COUNT(*) FROM trade_settlement_quote WHERE tenant_id=#{tenant} AND user_id=#{user} AND bound_order_id IS NULL AND expires_at>#{now} AND snapshot IS NOT NULL")
    Long countActive(@Param("tenant") Long tenant,@Param("user") Long user,@Param("now") java.time.LocalDateTime now);
    @Update("UPDATE trade_settlement_quote SET snapshot=NULL WHERE tenant_id=#{tenant} AND user_id=#{user} AND bound_order_id IS NULL "
        + "AND snapshot IS NOT NULL AND (expires_at<=#{now} OR cart_version<#{version}) ORDER BY expires_at,id LIMIT 50")
    int pruneSnapshots(@Param("tenant") Long tenant,@Param("user") Long user,@Param("version") Long version,@Param("now") java.time.LocalDateTime now);
    @Insert("INSERT INTO trade_settlement_quote(tenant_id,user_id,quote_id,request_id,request_digest,cart_version,address_id,coupon_id,snapshot,created_at,expires_at) "
        + "VALUES(#{row.tenantId},#{row.userId},#{row.quoteId},#{row.requestId},#{row.requestDigest},#{row.cartVersion},#{row.addressId},#{row.couponId},CAST(#{row.snapshot} AS JSON),#{row.createdAt},#{row.expiresAt})")
    @Options(useGeneratedKeys=true,keyProperty="row.id") int insert(@Param("row") TradeSettlementQuoteDO row);
}
