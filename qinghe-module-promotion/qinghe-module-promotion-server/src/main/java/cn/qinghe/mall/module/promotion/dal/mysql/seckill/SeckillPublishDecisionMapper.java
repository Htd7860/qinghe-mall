package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillStockReceiptDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 原活动启停条件更新职责；最终裁定与原审计同事务，全部SKU回执一次读取。 @author halfninety */
@Mapper
public interface SeckillPublishDecisionMapper {
    @Select("SELECT r.* FROM promotion_seckill_stock_receipt r WHERE r.tenant_id=#{tenant} AND r.activity_id=#{activity} "
            + "AND r.generation=#{generation} AND NOT EXISTS (SELECT 1 FROM promotion_seckill_stock_receipt n "
            + "WHERE n.tenant_id=r.tenant_id AND n.activity_id=r.activity_id AND n.generation=r.generation "
            + "AND n.sku_id=r.sku_id AND n.product_version>r.product_version) ORDER BY r.sku_id LIMIT 201")
    List<SeckillStockReceiptDO> latestAll(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Select("SELECT COUNT(*) FROM promotion_seckill_activity_audit WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND action=#{action} AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.requestId'))=#{request}")
    Long completed(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("action") String action,@Param("request") String request);

    @Update("UPDATE promotion_seckill_activity SET status=20,version=version+1,updater=#{actor},update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} AND publish_operation_no=#{request} "
            + "AND status=10 AND version=#{expected} AND version<9007199254740991 AND deleted=b'0' AND start_time>UTC_TIMESTAMP(3)")
    int publish(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor);

    @Update("UPDATE promotion_seckill_activity SET status=50,version=version+1,updater=#{actor},update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} AND publish_operation_no=#{request} "
            + "AND status=60 AND version=#{expected} AND version<9007199254740991 AND deleted=b'0'")
    int settle(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor);

    @Update("UPDATE promotion_seckill_activity SET status=60,version=version+1,stop_reason=#{reason},stopped_by=#{actor},"
            + "updater=#{actor},update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} "
            + "AND publish_operation_no=#{request} AND status=20 AND version=#{expected} AND version<9007199254740991 "
            + "AND deleted=b'0' AND redis_epoch=0 AND start_time>UTC_TIMESTAMP(3)")
    int withdraw(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor,@Param("reason") String reason);

    @Update("UPDATE promotion_seckill_activity_sku SET allocated_quantity=0,available_quantity=0,returned_quantity=0,allocation_status=0,"
            + "allocation_operation_no=NULL,version=version+1,update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND allocation_status=40 AND allocated_quantity=returned_quantity AND available_quantity=0 AND held_quantity=0 "
            + "AND sold_quantity=0 AND return_pending_quantity=0 AND version<9007199254740991")
    int resetSettledSkus(@Param("tenant") Long tenant,@Param("activity") Long activity);

    @Update("UPDATE promotion_seckill_activity SET status=0,publish_operation_no=NULL,version=version+1,"
            + "updater=#{actor},update_time=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND id=#{activity} AND generation=#{generation} "
            + "AND publish_operation_no=#{request} AND status=50 AND version=#{expected} AND redis_epoch=0 "
            + "AND version<9007199254740991 AND deleted=b'0'")
    int reopen(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation,
            @Param("request") String request,@Param("expected") Long expected,@Param("actor") String actor);

    @Select("SELECT to_version FROM promotion_seckill_activity_audit WHERE tenant_id=#{tenant} AND activity_id=#{activity} "
            + "AND action='REOPEN' AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.requestId'))=#{request} ORDER BY to_version LIMIT 1")
    Long reopenedVersion(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("request") String request);
}
