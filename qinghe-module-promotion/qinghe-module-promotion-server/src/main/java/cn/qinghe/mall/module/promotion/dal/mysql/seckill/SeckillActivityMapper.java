package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillActivityPageReqVO;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillActivityPoolSummary;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 原活动/活动商品的插入、修改、查询职责，限定草稿且所有 SQL 显式租户。 @author halfninety */
@Mapper
public interface SeckillActivityMapper {
    String PAGE_WHERE = " WHERE tenant_id=#{tenant} AND deleted=b'0' "
            + "<if test='query.name != null'> AND name LIKE CONCAT('%',#{query.name},'%')</if>"
            + "<if test='query.status != null'> AND status=#{query.status}</if>";

    @Select("<script>SELECT COUNT(*) FROM promotion_seckill_activity"+PAGE_WHERE+"</script>")
    Long countPage(@Param("tenant") Long tenant,@Param("query") SeckillActivityPageReqVO query);

    @Select("<script>SELECT * FROM promotion_seckill_activity"+PAGE_WHERE+" ORDER BY id DESC LIMIT #{offset},#{limit}</script>")
    List<SeckillActivityDO> page(@Param("tenant") Long tenant,@Param("query") SeckillActivityPageReqVO query,
            @Param("offset") Integer offset,@Param("limit") Integer limit);

    @Select("<script>SELECT activity_id,COUNT(*) AS sku_count,SUM(planned_quantity) AS planned_quantity,"
            + "SUM(allocated_quantity) AS allocated_quantity,SUM(available_quantity) AS available_quantity,"
            + "SUM(held_quantity) AS held_quantity,SUM(sold_quantity) AS sold_quantity,SUM(returned_quantity) AS returned_quantity,"
            + "SUM(return_pending_quantity) AS return_pending_quantity FROM promotion_seckill_activity_sku "
            + "WHERE tenant_id=#{tenant} AND activity_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY activity_id</script>")
    List<SeckillActivityPoolSummary> summarizePage(@Param("tenant") Long tenant,@Param("ids") List<Long> ids);

    @Insert("INSERT INTO promotion_seckill_activity(tenant_id,activity_no,name,product_spu_id,product_spu_version,product_name,pic_url,"
            + "start_time,end_time,single_limit,member_limit,status,version,generation,redis_epoch,creator,updater,create_time,update_time) "
            + "VALUES(#{row.tenantId},#{row.activityNo},#{row.name},#{row.productSpuId},#{row.productSpuVersion},#{row.productName},#{row.picUrl},"
            + "#{row.startTime},#{row.endTime},#{row.singleLimit},#{row.memberLimit},0,0,0,0,#{row.creator},#{row.updater},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insert(@Param("row") SeckillActivityDO row);

    @Select("SELECT * FROM promotion_seckill_activity WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=b'0' FOR UPDATE")
    SeckillActivityDO lock(@Param("tenant") Long tenant,@Param("id") Long id);

    @Select("SELECT * FROM promotion_seckill_activity WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=b'0'")
    SeckillActivityDO get(@Param("tenant") Long tenant,@Param("id") Long id);

    @Select("SELECT * FROM promotion_seckill_activity_sku WHERE tenant_id=#{tenant} AND activity_id=#{id} ORDER BY sku_id LIMIT 201")
    List<SeckillActivitySkuDO> skus(@Param("tenant") Long tenant,@Param("id") Long id);

    @Update("UPDATE promotion_seckill_activity SET name=#{row.name},product_spu_id=#{row.productSpuId},product_spu_version=#{row.productSpuVersion},"
            + "product_name=#{row.productName},pic_url=#{row.picUrl},start_time=#{row.startTime},end_time=#{row.endTime},single_limit=#{row.singleLimit},"
            + "member_limit=#{row.memberLimit},version=version+1,updater=#{row.updater},update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{row.tenantId} AND id=#{row.id} AND deleted=b'0' AND status=0 AND publish_operation_no IS NULL "
            + "AND version=#{expected} AND version<9007199254740991")
    int updateDraft(@Param("row") SeckillActivityDO row,@Param("expected") Long expected);

    // 草稿父锁内确认全部当前池余额为0；旧代次完整配置/回执/流水另行不可变保留。
    @Delete("DELETE FROM promotion_seckill_activity_sku WHERE tenant_id=#{tenant} AND activity_id=#{id} "
            + "AND allocated_quantity=0 AND available_quantity=0 AND held_quantity=0 AND sold_quantity=0 AND returned_quantity=0 "
            + "AND return_pending_quantity=0 AND allocation_status=0 AND allocation_operation_no IS NULL")
    int removeDraftSkus(@Param("tenant") Long tenant,@Param("id") Long id);

    @Insert("INSERT INTO promotion_seckill_activity_sku(tenant_id,activity_id,sku_id,sku_version,spec_snapshot,price_cent,planned_quantity,"
            + "allocated_quantity,available_quantity,held_quantity,sold_quantity,returned_quantity,return_pending_quantity,allocation_status,version,create_time,update_time) "
            + "VALUES(#{row.tenantId},#{row.activityId},#{row.skuId},#{row.skuVersion},CAST(#{row.specSnapshot} AS JSON),#{row.priceCent},#{row.plannedQuantity},"
            + "0,0,0,0,0,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
    int insertSku(@Param("row") SeckillActivitySkuDO row);

    @Insert("INSERT INTO promotion_seckill_activity_audit(tenant_id,activity_id,actor_id,action,from_version,to_version,details,create_time) "
            + "VALUES(#{tenant},#{id},#{actor},#{action},#{from},#{to},CAST(#{details} AS JSON),UTC_TIMESTAMP(3))")
    int audit(@Param("tenant") Long tenant,@Param("id") Long id,@Param("actor") Long actor,@Param("action") String action,
            @Param("from") Long from,@Param("to") Long to,@Param("details") String details);
}
