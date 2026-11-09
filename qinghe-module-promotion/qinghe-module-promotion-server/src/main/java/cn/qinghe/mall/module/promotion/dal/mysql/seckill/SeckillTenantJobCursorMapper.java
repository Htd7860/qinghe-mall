package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 直接择取交易任务固定游标；全局方法仅枚举本地义务租户，业务处理仍在明确tenant上下文内。 */
@Mapper
public interface SeckillTenantJobCursorMapper {
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT UTC_TIMESTAMP(3)") LocalDateTime now();

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COALESCE(MAX(top_id),0) FROM (SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_outbox "
            + "WHERE #{code}='SECKILL_COMMAND_PUBLISH' AND tenant_id BETWEEN 1 AND 9007199254740991 AND status IN('PENDING','SENDING','MANUAL') "
            + "UNION ALL SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_order_result WHERE #{code}='CLOSED_CACHE_SYNC' "
            + "AND tenant_id BETWEEN 1 AND 9007199254740991 AND cache_sync_state='PENDING' "
            + "UNION ALL SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_request WHERE #{code}='REQUEST_RECONCILE' "
            + "AND tenant_id BETWEEN 1 AND 9007199254740991 AND status IN(10,60) AND order_id IS NULL AND reconcile_deadline<=UTC_TIMESTAMP(3) "
            + "UNION ALL SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_admission_intent WHERE #{code}='INTENT_RECOVERY' "
            + "AND tenant_id BETWEEN 1 AND 9007199254740991 AND cache_sync_state='PENDING' AND (status IN(10,20) OR quote_expires_at<=UTC_TIMESTAMP(3)) "
            + "UNION ALL SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_request WHERE #{code}='ORDER_CACHE_SYNC' "
            + "AND tenant_id BETWEEN 1 AND 9007199254740991 AND resource_cache_state='PENDING' AND resource_state IN('PAID','CANCELLED') "
            + "UNION ALL SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_request WHERE #{code}='ORDER_RESOURCE_SYNC' "
            + "AND tenant_id BETWEEN 1 AND 9007199254740991 AND order_id IS NOT NULL AND resource_state='HELD' "
            + "AND (next_resource_check_at<=UTC_TIMESTAMP(3) OR resource_check_attempts>=10) "
            + "UNION ALL SELECT MAX(tenant_id) AS top_id FROM promotion_seckill_pool_return WHERE #{code}='POOL_RETURN_RECOVERY' AND source_kind='POOL' "
            + "AND tenant_id BETWEEN 1 AND 9007199254740991 AND (state='PENDING' OR (state='CONFIRMED' AND cache_state='PENDING')) "
            + "UNION ALL SELECT MAX(p.tenant_id) AS top_id"+SeckillPoolReturnMapper.PREPARABLE
            + " AND #{code}='POOL_RETURN_RECOVERY' AND p.tenant_id BETWEEN 1 AND 9007199254740991 "
            + "UNION ALL SELECT MAX(a.tenant_id) AS top_id"+SeckillClearingMapper.CANDIDATE_FROM
            + " AND #{code}='POOL_RETURN_RECOVERY' AND a.tenant_id BETWEEN 1 AND 9007199254740991 "
            + "UNION ALL SELECT MAX(a.tenant_id) AS top_id"+SeckillActivityExpiryMapper.DUE_FROM
            + " AND #{code}='ACTIVITY_EXPIRE' AND a.tenant_id BETWEEN 1 AND 9007199254740991) ids")
    Long ceiling(@Param("code") String code);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT tenant_id FROM ((SELECT DISTINCT tenant_id FROM promotion_seckill_outbox WHERE #{code}='SECKILL_COMMAND_PUBLISH' "
            + "AND tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} AND status IN('PENDING','SENDING','MANUAL') "
            + "ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT tenant_id FROM promotion_seckill_order_result WHERE #{code}='CLOSED_CACHE_SYNC' "
            + "AND tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} AND cache_sync_state='PENDING' "
            + "ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT tenant_id FROM promotion_seckill_request WHERE #{code}='REQUEST_RECONCILE' "
            + "AND tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} AND status IN(10,60) "
            + "AND order_id IS NULL AND reconcile_deadline<=UTC_TIMESTAMP(3) ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT tenant_id "
            + "FROM promotion_seckill_admission_intent WHERE #{code}='INTENT_RECOVERY' AND tenant_id>#{after} AND tenant_id<=#{ceiling} "
            + "AND MOD(tenant_id,#{total})=#{shard} AND cache_sync_state='PENDING' AND (status IN(10,20) OR quote_expires_at<=UTC_TIMESTAMP(3)) "
            + "ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT tenant_id FROM promotion_seckill_request WHERE #{code}='ORDER_CACHE_SYNC' "
            + "AND tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} "
            + "AND resource_cache_state='PENDING' AND resource_state IN('PAID','CANCELLED') ORDER BY tenant_id LIMIT 5) "
            + "UNION (SELECT DISTINCT tenant_id FROM promotion_seckill_request WHERE #{code}='ORDER_RESOURCE_SYNC' "
            + "AND tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} "
            + "AND order_id IS NOT NULL AND resource_state='HELD' AND (next_resource_check_at<=UTC_TIMESTAMP(3) OR resource_check_attempts>=10) "
            + "ORDER BY tenant_id LIMIT 5) UNION (SELECT DISTINCT tenant_id FROM promotion_seckill_pool_return WHERE #{code}='POOL_RETURN_RECOVERY' AND source_kind='POOL' "
            + "AND tenant_id>#{after} AND tenant_id<=#{ceiling} AND MOD(tenant_id,#{total})=#{shard} "
            + "AND (state='PENDING' OR (state='CONFIRMED' AND cache_state='PENDING')) ORDER BY tenant_id LIMIT 5) "
            + "UNION (SELECT DISTINCT p.tenant_id"+SeckillPoolReturnMapper.PREPARABLE
            + " AND #{code}='POOL_RETURN_RECOVERY' AND p.tenant_id>#{after} AND p.tenant_id<=#{ceiling} "
            + "AND MOD(p.tenant_id,#{total})=#{shard} ORDER BY p.tenant_id LIMIT 5) "
            + "UNION (SELECT DISTINCT a.tenant_id"+SeckillClearingMapper.CANDIDATE_FROM
            + " AND #{code}='POOL_RETURN_RECOVERY' AND a.tenant_id>#{after} AND a.tenant_id<=#{ceiling} "
            + "AND MOD(a.tenant_id,#{total})=#{shard} ORDER BY a.tenant_id LIMIT 5) "
            + "UNION (SELECT DISTINCT a.tenant_id"+SeckillActivityExpiryMapper.DUE_FROM
            + " AND #{code}='ACTIVITY_EXPIRE' AND a.tenant_id>#{after} AND a.tenant_id<=#{ceiling} "
            + "AND MOD(a.tenant_id,#{total})=#{shard} ORDER BY a.tenant_id LIMIT 5)) ids ORDER BY tenant_id LIMIT 5")
    List<Long> tenants(@Param("code") String code,@Param("after") Long after,@Param("ceiling") Long ceiling,@Param("shard") int shard,@Param("total") int total);

    @InterceptorIgnore(tenantLine="true")
    // Keep existing progress/lease untouched while taking a write lock before FOR UPDATE.
    @Insert("INSERT INTO promotion_seckill_job_cursor(job_code,shard_index,shard_total,last_tenant_id,ceiling_tenant_id,update_time) "
            + "VALUES(#{code},#{shard},#{total},0,0,#{now}) ON DUPLICATE KEY UPDATE job_code=job_code")
    int initialize(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("now") LocalDateTime now);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT * FROM promotion_seckill_job_cursor WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} FOR UPDATE")
    SeckillTenantJobCursorDO lock(@Param("code") String code,@Param("shard") int shard,@Param("total") int total);

    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE promotion_seckill_job_cursor SET last_tenant_id=#{after},ceiling_tenant_id=#{ceiling},lease_owner=#{owner},lease_until=#{until},update_time=#{now} "
            + "WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} AND (lease_until IS NULL OR lease_until<=#{now})")
    int claim(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("after") Long after,@Param("ceiling") Long ceiling,
            @Param("owner") String owner,@Param("until") LocalDateTime until,@Param("now") LocalDateTime now);

    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE promotion_seckill_job_cursor SET last_tenant_id=#{after},lease_until=#{until},update_time=#{now} "
            + "WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} AND lease_owner=#{owner} AND lease_until>#{now} "
            + "AND last_tenant_id<=#{after} AND #{after}<=ceiling_tenant_id")
    int advance(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("owner") String owner,
            @Param("after") Long after,@Param("until") LocalDateTime until,@Param("now") LocalDateTime now);

    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE promotion_seckill_job_cursor SET lease_owner=NULL,lease_until=NULL,update_time=#{now} "
            + "WHERE job_code=#{code} AND shard_index=#{shard} AND shard_total=#{total} AND lease_owner=#{owner}")
    int release(@Param("code") String code,@Param("shard") int shard,@Param("total") int total,@Param("owner") String owner,@Param("now") LocalDateTime now);

    // Concurrent cache jobs share this row; never reset the version or any keyset position.
    @Insert("INSERT INTO promotion_seckill_cache_cursor(tenant_id,version,update_time) "
            + "VALUES(#{tenant},0,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
    int initializeCache(@Param("tenant") Long tenant);
    @Select("SELECT * FROM promotion_seckill_cache_cursor WHERE tenant_id=#{tenant} FOR UPDATE")
    SeckillCacheJobCursorDO lockCache(@Param("tenant") Long tenant);
    @Update("UPDATE promotion_seckill_cache_cursor SET after_time=#{afterTime},after_event=#{afterEvent},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND version=#{version} AND version<9007199254740991")
    int advanceCache(@Param("tenant") Long tenant,@Param("version") Long version,@Param("afterTime") LocalDateTime afterTime,@Param("afterEvent") String afterEvent);

    /** Independent keyset position; a shared version detects concurrent closed-cache progress without overwriting either position. */
    @Update("UPDATE promotion_seckill_cache_cursor SET after_request=#{afterRequest},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND version=#{version} AND version<9007199254740991")
    int advanceOrderCache(@Param("tenant") Long tenant,@Param("version") Long version,@Param("afterRequest") String afterRequest);

    @Update("UPDATE promotion_seckill_cache_cursor SET after_return=#{afterReturn},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND version=#{version} AND version<9007199254740991")
    int advancePoolReturn(@Param("tenant") Long tenant,@Param("version") Long version,@Param("afterReturn") String afterReturn);

    @Update("UPDATE promotion_seckill_cache_cursor SET after_pool_id=#{afterPool},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND version=#{version} AND version<9007199254740991")
    int advancePoolPreparation(@Param("tenant") Long tenant,@Param("version") Long version,@Param("afterPool") Long afterPool);

    @Update("UPDATE promotion_seckill_cache_cursor SET after_expiry_activity=#{afterActivity},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND version=#{version} AND version<9007199254740991")
    int advanceExpiry(@Param("tenant") Long tenant,@Param("version") Long version,@Param("afterActivity") Long afterActivity);

    @Update("UPDATE promotion_seckill_cache_cursor SET after_clearing_activity=#{afterActivity},version=version+1,update_time=UTC_TIMESTAMP(3) "
            + "WHERE tenant_id=#{tenant} AND version=#{version} AND version<9007199254740991")
    int advanceClearing(@Param("tenant") Long tenant,@Param("version") Long version,@Param("afterActivity") Long afterActivity);
}
