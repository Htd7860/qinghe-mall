package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillPublishRecordDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/**
 * 沿用原validateProductConflict职责；按tenant/SKU持久互斥行串行检查半开区间，空查询不是锁。
 * 先活动父锁、再按SKU升序取guard；冲突活动只读不反向取父锁。
 * @author halfninety
 */
@Mapper
public interface SeckillPublishMapper {
    @Insert("INSERT INTO promotion_seckill_sku_guard(tenant_id,sku_id,create_time) VALUES(#{tenant},#{sku},UTC_TIMESTAMP(3)) "
            + "ON DUPLICATE KEY UPDATE sku_id=sku_id")
    int ensureGuard(@Param("tenant") Long tenant,@Param("sku") Long sku);

    @Select("SELECT sku_id FROM promotion_seckill_sku_guard WHERE tenant_id=#{tenant} AND sku_id=#{sku} FOR UPDATE")
    Long lockGuard(@Param("tenant") Long tenant,@Param("sku") Long sku);

    // 已停止但未清算的原活动仍保留时间占用，待后续确认义务结清后才解除。
    @Select("SELECT a.id FROM promotion_seckill_activity a JOIN promotion_seckill_activity_sku s "
            + "ON s.tenant_id=a.tenant_id AND s.activity_id=a.id WHERE a.tenant_id=#{tenant} AND s.tenant_id=#{tenant} "
            + "AND s.sku_id=#{sku} AND a.id<>#{activity} AND a.deleted=b'0' AND a.status IN(10,20,30,40,60) "
            + "AND a.start_time<#{end} AND a.end_time>#{start} LIMIT 1")
    Long conflict(@Param("tenant") Long tenant,@Param("sku") Long sku,@Param("activity") Long activity,
            @Param("start") LocalDateTime start,@Param("end") LocalDateTime end);

    @Select("SELECT * FROM promotion_seckill_publish_record WHERE tenant_id=#{tenant} AND request_id=#{request}")
    SeckillPublishRecordDO get(@Param("tenant") Long tenant,@Param("request") String request);

    @Insert("INSERT INTO promotion_seckill_publish_record(tenant_id,request_id,activity_id,actor_id,from_version,prepared_version,"
            + "generation,request_snapshot,configuration_snapshot,create_time) VALUES(#{row.tenantId},#{row.requestId},#{row.activityId},#{row.actorId},"
            + "#{row.fromVersion},#{row.preparedVersion},#{row.generation},CAST(#{row.requestSnapshot} AS JSON),CAST(#{row.configurationSnapshot} AS JSON),UTC_TIMESTAMP(3))")
    int insert(@Param("row") SeckillPublishRecordDO row);

    @Update("UPDATE promotion_seckill_activity SET status=10,version=version+1,generation=generation+1,publish_operation_no=#{request},"
            + "updater=#{actor},update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND id=#{id} AND version=#{expected} "
            + "AND status=0 AND generation<2147483647 AND publish_operation_no IS NULL AND deleted=b'0' AND start_time>UTC_TIMESTAMP(3) "
            + "AND version<9007199254740991")
    int prepare(@Param("tenant") Long tenant,@Param("id") Long id,@Param("expected") Long expected,
            @Param("request") String request,@Param("actor") String actor);

    @Update("UPDATE promotion_seckill_activity_sku SET allocation_status=10,allocation_operation_no=#{operation},"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND sku_id=#{sku} "
            + "AND version=#{expected} AND version<9007199254740991 AND allocation_status=0 AND allocation_operation_no IS NULL "
            + "AND allocated_quantity=0 AND available_quantity=0 AND held_quantity=0 AND sold_quantity=0 "
            + "AND returned_quantity=0 AND return_pending_quantity=0")
    int prepareSku(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("sku") Long sku,
            @Param("expected") Long expected,@Param("operation") String operation);
}
