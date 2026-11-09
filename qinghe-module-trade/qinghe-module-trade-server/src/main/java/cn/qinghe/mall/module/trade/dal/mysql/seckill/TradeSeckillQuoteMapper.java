package cn.qinghe.mall.module.trade.dal.mysql.seckill;

import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillQuoteDO;
import org.apache.ibatis.annotations.*;

/** 所有查询绑定tenant+member；写入由同会员父锁串行化，原请求记录不被删除或续期。 */
@Mapper
public interface TradeSeckillQuoteMapper {
    @Select("SELECT * FROM trade_seckill_quote WHERE tenant_id=#{tenant} AND member_id=#{member} AND request_id=#{request}")
    TradeSeckillQuoteDO request(@Param("tenant") Long tenant,@Param("member") Long member,@Param("request") String request);

    @Select("SELECT * FROM trade_seckill_quote WHERE tenant_id=#{tenant} AND member_id=#{member} AND quote_id=#{quote}")
    TradeSeckillQuoteDO quote(@Param("tenant") Long tenant,@Param("member") Long member,@Param("quote") String quote);

    @Select("SELECT * FROM trade_seckill_quote WHERE tenant_id=#{tenant} AND member_id=#{member} AND quote_id=#{quote} FOR UPDATE")
    TradeSeckillQuoteDO lock(@Param("tenant") Long tenant,@Param("member") Long member,@Param("quote") String quote);

    @Update("UPDATE trade_seckill_quote SET bound_request_no=#{request} WHERE tenant_id=#{tenant} AND member_id=#{member} "
            + "AND quote_id=#{quote} AND quote_digest=#{digest} AND bound_request_no IS NULL AND bound_order_id IS NULL "
            + "AND snapshot_cipher IS NOT NULL AND created_at<=UTC_TIMESTAMP(3) AND expires_at>UTC_TIMESTAMP(3)")
    int bind(@Param("tenant") Long tenant,@Param("member") Long member,@Param("quote") String quote,
            @Param("digest") String digest,@Param("request") String request);

    /** 仅在原栅栏已关闭的同一事务调用；永久绑定原申请，不能把报价恢复成可用。 */
    @Update("UPDATE trade_seckill_quote SET bound_request_no=#{request},snapshot_cipher=NULL "
            + "WHERE tenant_id=#{tenant} AND member_id=#{member} AND quote_id=#{quote} AND quote_digest=#{digest} "
            + "AND (bound_request_no IS NULL OR bound_request_no=#{request}) AND bound_order_id IS NULL")
    int closeSnapshot(@Param("tenant") Long tenant,@Param("member") Long member,@Param("quote") String quote,
            @Param("digest") String digest,@Param("request") String request);

    /** 同会员父锁内清尚未绑定且已过期的密文；绑定事务也取该父锁，元数据/墓碑不删。 */
    @Update("UPDATE trade_seckill_quote SET snapshot_cipher=NULL WHERE tenant_id=#{tenant} AND member_id=#{member} "
            + "AND bound_request_no IS NULL AND bound_order_id IS NULL AND expires_at<=UTC_TIMESTAMP(3) AND snapshot_cipher IS NOT NULL "
            + "ORDER BY expires_at,quote_id LIMIT 50")
    int clearExpired(@Param("tenant") Long tenant,@Param("member") Long member);

    @Select("SELECT COUNT(*) FROM trade_seckill_quote WHERE tenant_id=#{tenant} AND member_id=#{member} "
            + "AND bound_request_no IS NULL AND expires_at>UTC_TIMESTAMP(3) AND snapshot_cipher IS NOT NULL")
    Long active(@Param("tenant") Long tenant,@Param("member") Long member);

    @Select("SELECT UTC_TIMESTAMP(3)")
    java.time.LocalDateTime now();

    @Insert("INSERT INTO trade_seckill_quote(tenant_id,member_id,quote_id,request_id,request_digest,activity_id,generation,"
            + "activity_version,redis_epoch,sku_id,quantity,price_cent,goods_amount,shipping_amount,pay_amount,quote_digest,"
            + "snapshot_cipher,key_id,created_at,expires_at,bound_request_no,bound_order_id) VALUES(#{row.tenantId},#{row.memberId},"
            + "#{row.quoteId},#{row.requestId},#{row.requestDigest},#{row.activityId},#{row.generation},#{row.activityVersion},"
            + "#{row.redisEpoch},#{row.skuId},#{row.quantity},#{row.priceCent},#{row.goodsAmount},#{row.shippingAmount},"
            + "#{row.payAmount},#{row.quoteDigest},#{row.snapshotCipher},#{row.keyId},#{row.createdAt},#{row.expiresAt},NULL,NULL)")
    int insert(@Param("row") TradeSeckillQuoteDO row);
}
