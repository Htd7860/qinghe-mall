package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 本店公开发布的当前代次；旁路、草稿或失败补偿记录不参与浏览。 */
@Mapper
public interface AppSeckillActivityMapper {
    String FROM=" FROM promotion_seckill_activity a LEFT JOIN promotion_seckill_redis_epoch e ON e.tenant_id=a.tenant_id "
            + "AND e.activity_id=a.id AND e.generation=a.generation AND e.epoch=a.redis_epoch AND e.publish_request_id=a.publish_operation_no "
            + "WHERE a.tenant_id=#{tenant} AND a.deleted=b'0' AND a.status IN(20,30,40,50) "
            + "AND EXISTS(SELECT 1 FROM promotion_seckill_activity_audit d WHERE d.tenant_id=a.tenant_id AND d.activity_id=a.id "
            + "AND d.action='PUBLISH' AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.requestId'))=a.publish_operation_no) "
            + "AND NOT EXISTS(SELECT 1 FROM promotion_seckill_activity_audit d WHERE d.tenant_id=a.tenant_id AND d.activity_id=a.id "
            + "AND d.action='WITHDRAW' AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.requestId'))=a.publish_operation_no)";
    String SELECT="SELECT a.*,e.status AS runtime_stage,(SELECT MIN(s.price_cent) FROM promotion_seckill_activity_sku s "
            + "WHERE s.tenant_id=a.tenant_id AND s.activity_id=a.id AND s.allocation_status=20) AS min_price_cent";
    @Select("SELECT UTC_TIMESTAMP(3)") LocalDateTime now();
    @Select("SELECT COUNT(*)"+FROM) Long count(@Param("tenant") Long tenant);
    @Select(SELECT+FROM+" ORDER BY a.id DESC LIMIT #{offset},#{limit}")
    List<Row> page(@Param("tenant") Long tenant,@Param("offset") int offset,@Param("limit") int limit);
    @Select(SELECT+FROM+" AND a.id=#{activity}")
    Row get(@Param("tenant") Long tenant,@Param("activity") Long activity);
    @Data @EqualsAndHashCode(callSuper=true) class Row extends SeckillActivityDO {
        private Integer runtimeStage;
        private Integer minPriceCent;
    }
}
