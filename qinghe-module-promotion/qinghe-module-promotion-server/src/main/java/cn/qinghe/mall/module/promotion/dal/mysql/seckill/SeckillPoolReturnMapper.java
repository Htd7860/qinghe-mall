package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillPoolReturnDO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillActivitySkuDO;
import org.apache.ibatis.annotations.*;

/** 原活动→SKU父锁内预留；目标不可改写，网络超时不能解除预留。 */
@Mapper
public interface SeckillPoolReturnMapper {
    /** 同一条件供全局租户发现和本租户有界扫描使用，数量仍由prepare父锁内重新确定。 */
    String PREPARABLE=" FROM promotion_seckill_activity_sku p JOIN promotion_seckill_activity a ON a.tenant_id=p.tenant_id AND a.id=p.activity_id "
            + "JOIN promotion_seckill_redis_epoch e ON e.tenant_id=a.tenant_id AND e.activity_id=a.id AND e.generation=a.generation AND e.epoch=a.redis_epoch "
            + "WHERE a.status=30 AND a.deleted=b'0' AND a.redis_epoch=1 AND e.status IN(30,40) AND p.allocation_status=20 "
            + "AND p.available_quantity>0 AND p.return_pending_quantity=0 AND p.allocated_quantity>p.returned_quantity "
            + "AND NOT EXISTS(SELECT 1 FROM promotion_seckill_pool_return r WHERE r.tenant_id=p.tenant_id AND r.activity_id=p.activity_id "
            + "AND r.generation=a.generation AND r.sku_id=p.sku_id AND r.state='PENDING')";

    @Select("<script>SELECT p.*"+PREPARABLE+" AND p.tenant_id=#{tenant} "
            + "<if test='after != null'>AND p.id &gt; #{after}</if> ORDER BY p.id LIMIT #{limit}</script>")
    java.util.List<SeckillActivitySkuDO> prepareCandidates(@Param("tenant") Long tenant,@Param("after") Long after,@Param("limit") int limit);

    @Select("SELECT * FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND return_no=#{number}")
    SeckillPoolReturnDO get(@Param("tenant") Long tenant,@Param("number") String returnNo);

    @Select("SELECT * FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND return_no=#{number} FOR UPDATE")
    SeckillPoolReturnDO lock(@Param("tenant") Long tenant,@Param("number") String returnNo);

    @Update("UPDATE promotion_seckill_activity_sku SET available_quantity=available_quantity-#{r.quantity},"
            + "return_pending_quantity=return_pending_quantity-#{r.quantity},returned_quantity=returned_quantity+#{r.quantity},"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND activity_id=#{r.activityId} AND sku_id=#{r.skuId} "
            + "AND version=#{version} AND version<9007199254740991 AND allocation_status=20 AND allocation_operation_no=#{r.allocationOperationNo} "
            + "AND returned_quantity=#{r.baseReturned} AND return_pending_quantity=#{r.quantity} AND available_quantity>=#{r.quantity} AND #{r.quantity}>0")
    int apply(@Param("r") SeckillPoolReturnDO original,@Param("version") Long poolVersion);

    @Update("UPDATE promotion_seckill_pool_return SET state='CONFIRMED',product_version=#{productVersion},product_status=#{productStatus},"
            + "confirmed_time=UTC_TIMESTAMP(3),cache_state='PENDING',version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND return_no=#{r.returnNo} AND state='PENDING' AND version=#{r.version} AND version<9007199254740991 "
            + "AND base_returned=#{r.baseReturned} AND target_returned=#{r.targetReturned} AND base_product_version=#{r.baseProductVersion}")
    int confirmed(@Param("r") SeckillPoolReturnDO original,@Param("productVersion") Long productVersion,@Param("productStatus") Integer productStatus);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,event_type,available_delta,returned_delta,"
            + "source_event_id,create_time) VALUES(#{r.tenantId},CONCAT('pool-return:',#{r.returnNo}),#{r.activityId},#{r.generation},#{r.skuId},"
            + "'POOL_RETURNED',-#{r.quantity},#{r.quantity},CONCAT('return:',#{r.returnNo}),UTC_TIMESTAMP(3))")
    int journal(@Param("r") SeckillPoolReturnDO original);

    String PROOF=" FROM promotion_seckill_pool_return r JOIN promotion_seckill_stock_receipt s "
            + "ON s.tenant_id=r.tenant_id AND s.activity_id=r.activity_id AND s.generation=r.generation AND s.sku_id=r.sku_id "
            + "AND s.product_version=r.product_version AND s.product_status=r.product_status AND s.returned_quantity=r.target_returned "
            + "AND s.allocated_quantity=JSON_EXTRACT(r.allocation_snapshot,'$.quantity') JOIN promotion_seckill_quota_entry e "
            + "ON e.tenant_id=r.tenant_id AND e.operation_no=CONCAT('pool-return:',r.return_no) AND e.activity_id=r.activity_id "
            + "AND e.generation=r.generation AND e.sku_id=r.sku_id AND e.event_type='POOL_RETURNED' AND e.source_event_id=CONCAT('return:',r.return_no) "
            + "AND e.available_delta=-r.quantity AND e.returned_delta=r.quantity AND e.allocated_delta=0 AND e.held_delta=0 "
            + "AND e.sold_delta=0 AND e.member_held_delta=0 AND e.member_paid_delta=0 AND e.member_id IS NULL AND e.request_no IS NULL "
            + "WHERE r.tenant_id=#{tenant} AND r.return_no=#{number} AND r.source_kind='POOL' AND r.state='CONFIRMED' AND r.confirmed_time IS NOT NULL "
            + "AND r.product_version=r.base_product_version+1 AND r.product_status IN(30,50) AND r.cache_state IN('PENDING','SYNCED')";
    @Select("SELECT r.*"+PROOF)
    SeckillPoolReturnDO confirmedProof(@Param("tenant") Long tenant,@Param("number") String returnNo);

    @Update("UPDATE promotion_seckill_pool_return SET cache_state='SYNCED',version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{r.tenantId} AND return_no=#{r.returnNo} AND state='CONFIRMED' AND cache_state='PENDING' "
            + "AND version=#{r.version} AND version<9007199254740991 AND product_version=#{r.productVersion} AND target_returned=#{r.targetReturned}")
    int cacheSynced(@Param("r") SeckillPoolReturnDO proof);

    @Select("<script>SELECT * FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND source_kind='POOL' AND "
            + "(state='PENDING' OR (state='CONFIRMED' AND cache_state='PENDING')) "
            + "<if test='after != null'>AND return_no &gt; #{after}</if> ORDER BY return_no LIMIT #{limit}</script>")
    java.util.List<SeckillPoolReturnDO> recoverable(@Param("tenant") Long tenant,@Param("after") String after,@Param("limit") int limit);

    @Select("SELECT * FROM promotion_seckill_pool_return WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND generation=#{generation} AND sku_id=#{sku} AND state='PENDING' LIMIT 1 FOR UPDATE")
    SeckillPoolReturnDO pending(@Param("tenant") Long tenant,@Param("activity") Long activity,
            @Param("generation") Integer generation,@Param("sku") Long sku);

    @Update("UPDATE promotion_seckill_activity_sku SET return_pending_quantity=return_pending_quantity+#{r.quantity},"
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{r.tenantId} AND activity_id=#{r.activityId} AND sku_id=#{r.skuId} "
            + "AND version=#{version} AND version<9007199254740991 AND allocation_status=20 AND allocation_operation_no=#{r.allocationOperationNo} "
            + "AND return_pending_quantity=0 AND returned_quantity=#{r.baseReturned} AND available_quantity>=#{r.quantity} AND #{r.quantity}>0")
    int reserve(@Param("r") SeckillPoolReturnDO original,@Param("version") Long poolVersion);

    @Insert("INSERT INTO promotion_seckill_pool_return(tenant_id,return_no,activity_id,generation,redis_epoch,sku_id,allocation_operation_no,allocation_snapshot,"
            + "quantity,base_returned,target_returned,base_product_version,state,cache_state,version,create_time,update_time) "
            + "VALUES(#{r.tenantId},#{r.returnNo},#{r.activityId},#{r.generation},#{r.redisEpoch},#{r.skuId},#{r.allocationOperationNo},CAST(#{r.allocationSnapshot} AS JSON),"
            + "#{r.quantity},#{r.baseReturned},#{r.targetReturned},#{r.baseProductVersion},'PENDING','NOT_REQUIRED',0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
    int insert(@Param("r") SeckillPoolReturnDO original);
}
