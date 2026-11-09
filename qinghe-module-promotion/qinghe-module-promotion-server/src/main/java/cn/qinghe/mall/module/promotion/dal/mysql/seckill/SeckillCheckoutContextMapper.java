package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO;
import org.apache.ibatis.annotations.*;

/** 同一数据库语句读取当前已发布/READY的单SKU；不读Redis，不作库存承诺。 */
@Mapper
public interface SeckillCheckoutContextMapper {
    @Select("SELECT a.tenant_id,#{member} AS member_id,a.id AS activity_id,a.generation,a.version AS activity_version,"
            + "a.redis_epoch,a.product_spu_id AS spu_id,a.product_spu_version AS spu_version,s.sku_id,s.sku_version,"
            + "s.price_cent,a.single_limit,a.member_limit,a.start_time,a.end_time,UTC_TIMESTAMP(3) AS server_time "
            + "FROM promotion_seckill_activity a JOIN promotion_seckill_activity_sku s ON s.tenant_id=a.tenant_id AND s.activity_id=a.id "
            + "JOIN promotion_seckill_redis_epoch e ON e.tenant_id=a.tenant_id AND e.activity_id=a.id AND e.generation=a.generation AND e.epoch=a.redis_epoch "
            + "WHERE a.tenant_id=#{tenant} AND a.id=#{activity} AND a.generation=#{generation} AND s.sku_id=#{sku} "
            + "AND a.deleted=b'0' AND a.status=20 AND a.redis_epoch=1 AND e.status=20 AND e.publish_request_id=a.publish_operation_no "
            + "AND s.allocation_status=20 AND a.start_time<=UTC_TIMESTAMP(3) AND a.end_time>UTC_TIMESTAMP(3)")
    SeckillCheckoutContextDTO current(@Param("tenant") Long tenant,@Param("member") Long member,
            @Param("activity") Long activity,@Param("generation") Integer generation,@Param("sku") Long sku);
}
