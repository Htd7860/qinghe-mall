package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishHistoryDTO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 固定活动、当前租户的原发布代次历史，按审计事实投影，不把当前状态套给旧请求。 */
@Mapper
public interface SeckillPublishHistoryMapper {
    @Select("SELECT COUNT(*) FROM promotion_seckill_publish_record WHERE tenant_id=#{tenant} AND activity_id=#{activity}")
    Long count(@Param("tenant") Long tenant,@Param("activity") Long activity);

    @Select("SELECT p.request_id,p.actor_id,p.from_version,p.prepared_version,p.generation,p.create_time AS prepared_at,"
            + "EXISTS(SELECT 1 FROM promotion_seckill_activity_audit a WHERE a.tenant_id=p.tenant_id AND a.activity_id=p.activity_id "
            + "AND a.action='PUBLISH' AND JSON_UNQUOTE(JSON_EXTRACT(a.details,'$.requestId'))=p.request_id) AS published,"
            + "EXISTS(SELECT 1 FROM promotion_seckill_activity_audit a WHERE a.tenant_id=p.tenant_id AND a.activity_id=p.activity_id "
            + "AND a.action='WITHDRAW' AND JSON_UNQUOTE(JSON_EXTRACT(a.details,'$.requestId'))=p.request_id) AS withdrawn,"
            + "EXISTS(SELECT 1 FROM promotion_seckill_activity_audit a WHERE a.tenant_id=p.tenant_id AND a.activity_id=p.activity_id "
            + "AND a.action IN('SETTLE','CLEARING_DONE') AND JSON_UNQUOTE(JSON_EXTRACT(a.details,'$.requestId'))=p.request_id) AS settled "
            + "FROM promotion_seckill_publish_record p WHERE p.tenant_id=#{tenant} AND p.activity_id=#{activity} "
            + "ORDER BY p.generation DESC LIMIT #{offset},#{limit}")
    List<SeckillPublishHistoryDTO> page(@Param("tenant") Long tenant,@Param("activity") Long activity,
            @Param("offset") Integer offset,@Param("limit") Integer limit);
}
