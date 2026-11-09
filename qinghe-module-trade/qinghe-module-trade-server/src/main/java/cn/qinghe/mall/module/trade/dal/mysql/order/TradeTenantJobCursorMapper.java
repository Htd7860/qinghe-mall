package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeTenantJobCursorDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 可信XXL调度专用：只枚举trade本地持久租户编号和维护四个固定任务游标。
 * 每条方法显式忽略租户拦截；业务恢复Mapper不使用此旁路，无Controller可调用此类。
 */
@Mapper
public interface TradeTenantJobCursorMapper {
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COALESCE(MAX(tenant_id),0) FROM trade_seckill_order_binding WHERE tenant_id BETWEEN 1 AND 9007199254740991 "
            + "AND state=0 AND order_id IS NULL AND quote_expires_at<=TIMESTAMPADD(MINUTE,-30,UTC_TIMESTAMP(3))")
    Long seckillBindingCeiling();
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT DISTINCT tenant_id FROM trade_seckill_order_binding WHERE tenant_id>#{after} AND tenant_id<=#{ceiling} "
            + "AND MOD(tenant_id,#{total})=#{shard} AND state=0 AND order_id IS NULL "
            + "AND quote_expires_at<=TIMESTAMPADD(MINUTE,-30,UTC_TIMESTAMP(3)) ORDER BY tenant_id LIMIT 5")
    List<Long> seckillBindingTenants(@Param("after") Long after,@Param("ceiling") Long ceiling,@Param("shard") int shard,@Param("total") int total);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COALESCE(MAX(tenant_id),0) FROM trade_seckill_outbox WHERE tenant_id BETWEEN 1 AND 9007199254740991 AND status IN('PENDING','SENDING','MANUAL')")
    Long seckillResultCeiling();
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT DISTINCT tenant_id FROM trade_seckill_outbox WHERE tenant_id>#{after} AND tenant_id<=#{ceiling} "
            + "AND MOD(tenant_id,#{total})=#{shard} AND status IN('PENDING','SENDING','MANUAL') ORDER BY tenant_id LIMIT 5")
    List<Long> seckillResultTenants(@Param("after") Long after,@Param("ceiling") Long ceiling,@Param("shard") int shard,@Param("total") int total);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COALESCE(MAX(top_id),0) FROM (SELECT MAX(tenant_id) AS top_id FROM trade_order_prepare WHERE tenant_id "
        +"BETWEEN 1 AND 9007199254740991 UNION ALL SELECT MAX(tenant_id) AS top_id FROM trade_order WHERE tenant_id BETWEEN 1"
        +" AND 9007199254740991 UNION ALL SELECT MAX(tenant_id) AS top_id FROM trade_order_outbox WHERE tenant_id BETWEEN 1 "
        +"AND 9007199254740991 UNION ALL SELECT MAX(tenant_id) AS top_id FROM trade_order_recovery WHERE tenant_id BETWEEN 1 "
        +"AND 9007199254740991) ids")
    Long ceiling();
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT tenant_id FROM ((SELECT DISTINCT tenant_id FROM trade_order_prepare WHERE tenant_id>#{after} AND "
        +"tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT "
        +"tenant_id FROM trade_order WHERE tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} "
        +"ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT tenant_id FROM trade_order_outbox WHERE tenant_id>#{after} AND "
        +"tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT "
        +"tenant_id FROM trade_order_recovery WHERE tenant_id>#{after} AND tenant_id<=#{ceiling} AND "
        +"MOD(tenant_id,#{total})=#{shard} ORDER BY tenant_id LIMIT 5)) ids ORDER BY tenant_id LIMIT 5")
    List<Long> tenants(@Param("after") Long after,@Param("ceiling") Long ceiling,@Param("shard") int shard,@Param("total") int total);
    @InterceptorIgnore(tenantLine="true")
    // Initialize and lock in the same transaction: acquire the existing row's write lock
    // without resetting its scan position or lease (INSERT IGNORE can upgrade shared locks).
    @Insert("INSERT INTO trade_tenant_job_cursor(job_code,shard_index,shard_total,last_tenant_id,ceiling_tenant_id,update_time) "
            + "VALUES(#{code},#{shard},#{total},0,0,#{now}) ON DUPLICATE KEY UPDATE job_code=job_code")
    int initialize(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("now") LocalDateTime now);
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT * FROM trade_tenant_job_cursor WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} FOR UPDATE")
    TradeTenantJobCursorDO lock(@Param("code") String code,@Param("shard") int shard,@Param("total") int total);
    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE trade_tenant_job_cursor SET last_tenant_id=#{after},ceiling_tenant_id=#{ceiling},lease_owner=#{owner},lease_until=#{until},update_time=#{now} "
        +"WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} AND (lease_until IS NULL OR lease_until<=#{now})")
    int claim(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("after") Long after,@Param("ceiling") Long ceiling,
        @Param("owner") String owner,@Param("until") LocalDateTime until,@Param("now") LocalDateTime now);
    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE trade_tenant_job_cursor SET last_tenant_id=#{after},lease_until=#{until},update_time=#{now} "
        +"WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} AND lease_owner=#{owner} AND lease_until>#{now} "
        +"AND last_tenant_id<=#{after} AND #{after}<=ceiling_tenant_id")
    int advance(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("owner") String owner,
        @Param("after") Long after,@Param("until") LocalDateTime until,@Param("now") LocalDateTime now);
    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE trade_tenant_job_cursor SET lease_owner=NULL,lease_until=NULL,update_time=#{now} WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} AND lease_owner=#{owner}")
    int release(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("owner") String owner,@Param("now") LocalDateTime now);
}
