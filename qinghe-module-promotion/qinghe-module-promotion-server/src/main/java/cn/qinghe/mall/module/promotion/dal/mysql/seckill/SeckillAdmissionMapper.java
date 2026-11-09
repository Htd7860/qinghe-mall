package cn.qinghe.mall.module.promotion.dal.mysql.seckill;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import org.apache.ibatis.annotations.*;

/** 受理账本白名单SQL；活动→SKU→个人额度→原意图/申请，同一事务写流水及事件。 */
@Mapper
public interface SeckillAdmissionMapper {
    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND request_no=#{request}")
    SeckillRequestDO forDispatch(@Param("tenant") Long tenant,@Param("request") String request);
    @Select("SELECT UTC_TIMESTAMP(3)")
    java.time.LocalDateTime now();
    @Select("SELECT * FROM promotion_seckill_activity_sku WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND sku_id=#{sku} FOR UPDATE")
    SeckillActivitySkuDO lockPool(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("sku") Long sku);
    @Select("SELECT COUNT(*) FROM (SELECT event_id FROM promotion_seckill_outbox WHERE tenant_id=#{tenant} "
            + "AND status IN('PENDING','SENDING','MANUAL') LIMIT #{limit}) AS admission_backlog")
    Long pendingEvents(@Param("tenant") Long tenant,@Param("limit") Integer limit);
    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND member_id=#{member} AND client_request_id=#{client}")
    SeckillRequestDO ownClient(@Param("tenant") Long tenant,@Param("member") Long member,@Param("client") String client);

    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND member_id=#{member} AND request_no=#{request}")
    SeckillRequestDO ownRequest(@Param("tenant") Long tenant,@Param("member") Long member,@Param("request") String request);

    @Select("SELECT COUNT(*) FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND member_id=#{member}")
    Long ownCount(@Param("tenant") Long tenant,@Param("member") Long member);
    @Select("SELECT * FROM promotion_seckill_request WHERE tenant_id=#{tenant} AND member_id=#{member} "
            + "ORDER BY accepted_time DESC,request_no DESC LIMIT #{offset},#{limit}")
    java.util.List<SeckillRequestDO> ownPage(@Param("tenant") Long tenant,@Param("member") Long member,
            @Param("offset") int offset,@Param("limit") int limit);

    @Insert("INSERT INTO promotion_seckill_member_quota(tenant_id,activity_id,generation,member_id,held_quantity,paid_quantity,version,create_time,update_time) "
            + "VALUES(#{tenant},#{activity},#{generation},#{member},0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) "
            + "ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
    int ensureQuota(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("member") Long member);

    @Select("SELECT * FROM promotion_seckill_member_quota WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} AND member_id=#{member} FOR UPDATE")
    SeckillMemberQuotaDO lockQuota(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("member") Long member);

    @Update("UPDATE promotion_seckill_member_quota SET held_quantity=held_quantity+#{quantity},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} AND member_id=#{member} "
            + "AND version=#{expected} AND version<9007199254740991 AND held_quantity+paid_quantity<=#{limit}-#{quantity} AND #{quantity}>0")
    int holdQuota(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,@Param("member") Long member,
            @Param("expected") Long expected,@Param("quantity") Integer quantity,@Param("limit") Integer limit);

    @Update("UPDATE promotion_seckill_activity_sku SET available_quantity=available_quantity-#{quantity},held_quantity=held_quantity+#{quantity}, "
            + "version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND sku_id=#{sku} "
            + "AND allocation_status=20 AND version=#{expected} AND version<9007199254740991 AND available_quantity-return_pending_quantity>=#{quantity} AND #{quantity}>0 "
            + "AND EXISTS(SELECT 1 FROM promotion_seckill_activity a JOIN promotion_seckill_redis_epoch e "
            + "ON e.tenant_id=a.tenant_id AND e.activity_id=a.id AND e.generation=a.generation AND e.epoch=a.redis_epoch "
            + "WHERE a.tenant_id=#{tenant} AND a.id=#{activity} AND a.generation=#{generation} AND a.redis_epoch=#{epoch} "
            + "AND a.version=#{activityVersion} AND a.status=20 AND a.deleted=b'0' AND e.status=20 AND e.init_token=#{token} "
            + "AND a.start_time<=UTC_TIMESTAMP(3) AND a.end_time>UTC_TIMESTAMP(3)) AND #{expires}>UTC_TIMESTAMP(3)")
    int holdPool(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("epoch") Long epoch,@Param("token") String token,@Param("activityVersion") Long activityVersion,
            @Param("sku") Long sku,@Param("expected") Long expected,@Param("quantity") Integer quantity,@Param("expires") java.time.LocalDateTime expires);

    @Insert("INSERT INTO promotion_seckill_request(tenant_id,request_no,member_id,client_request_id,request_digest,activity_id,generation,activity_version,redis_epoch,init_token,"
            + "sku_id,quantity,price_cent,goods_amount,shipping_amount,pay_amount,quote_id,quote_digest,quote_expires_time,reservation_token,status,order_id,fence_version,"
            + "accepted_time,reconcile_deadline,pay_deadline,next_retry_time,retry_count,claim_token,locked_until,failure_code,version,create_time,update_time) "
            + "VALUES(#{row.tenantId},#{row.requestNo},#{row.memberId},#{row.clientRequestId},#{row.requestDigest},#{row.activityId},#{row.generation},#{row.activityVersion},#{row.redisEpoch},#{row.initToken},"
            + "#{row.skuId},#{row.quantity},#{row.priceCent},#{row.goodsAmount},#{row.shippingAmount},#{row.payAmount},#{row.quoteId},#{row.quoteDigest},#{row.quoteExpiresTime},#{row.reservationToken},10,NULL,0,"
            + "#{row.acceptedTime},#{row.reconcileDeadline},#{row.payDeadline},#{row.acceptedTime},0,NULL,NULL,NULL,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
    int insertRequest(@Param("row") SeckillRequestDO row);

    @Insert("INSERT INTO promotion_seckill_quota_entry(tenant_id,operation_no,activity_id,generation,sku_id,member_id,request_no,event_type,available_delta,held_delta,member_held_delta,source_event_id,create_time) "
            + "VALUES(#{row.tenantId},CONCAT('accept:',#{row.requestNo}),#{row.activityId},#{row.generation},#{row.skuId},#{row.memberId},#{row.requestNo},'ACCEPT',-#{row.quantity},#{row.quantity},#{row.quantity},#{event},UTC_TIMESTAMP(3))")
    int journalAccepted(@Param("row") SeckillRequestDO row,@Param("event") String event);

    @Insert("INSERT INTO promotion_seckill_outbox(tenant_id,event_id,request_no,event_type,aggregate_version,schema_version,payload,status,attempts,available_at,replay_version,created_at) "
            + "VALUES(#{row.tenantId},#{row.eventId},#{row.requestNo},'SECKILL_CREATE',0,1,CAST(#{row.payload} AS JSON),'PENDING',0,#{row.availableAt},0,UTC_TIMESTAMP(3))")
    int insertCreateEvent(@Param("row") SeckillOutboxDO row);
}
