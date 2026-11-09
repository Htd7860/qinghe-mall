package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.controller.platform.seckill.vo.PlatformSeckillPageReqVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 仅平台专用只读方法忽略租户插件；调用方先验证平台0身份和专用权限。 */
@Mapper
public interface PlatformSeckillReadMapper {
    String WHERE=" WHERE a.deleted=b'0' AND a.tenant_id BETWEEN 1 AND 9007199254740991 "
            + "<if test='q.tenantId != null'> AND a.tenant_id=#{q.tenantId}</if>"
            + "<if test='q.name != null'> AND a.name LIKE CONCAT('%',#{q.name},'%')</if>"
            + "<if test='q.status != null'> AND a.status=#{q.status}</if>";
    @InterceptorIgnore(tenantLine="true")
    @Select("<script>SELECT COUNT(*) FROM promotion_seckill_activity a"+WHERE+"</script>")
    Long count(@Param("q") PlatformSeckillPageReqVO query);

    @InterceptorIgnore(tenantLine="true")
    @Select("<script>SELECT a.id,a.tenant_id,a.name,a.product_name,a.status,a.generation,a.version,a.start_time,a.end_time "
            +"FROM promotion_seckill_activity a"+WHERE+" ORDER BY a.id DESC LIMIT #{offset},#{limit}</script>")
    List<ActivityRow> page(@Param("q") PlatformSeckillPageReqVO query,@Param("offset") int offset,@Param("limit") int limit);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COUNT(*) FROM promotion_seckill_activity WHERE tenant_id=#{tenant} AND id=#{activity} AND deleted=b'0'")
    Long exists(@Param("tenant") Long tenant,@Param("activity") Long activity);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COUNT(*) FROM promotion_seckill_activity_audit WHERE tenant_id=#{tenant} AND activity_id=#{activity}")
    Long auditCount(@Param("tenant") Long tenant,@Param("activity") Long activity);

    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT id,actor_id,CASE WHEN actor_id=0 THEN 'system' WHEN JSON_UNQUOTE(JSON_EXTRACT(details,'$.actorRealm'))='platform' "
            +"AND JSON_EXTRACT(details,'$.actorTenantId')=0 THEN 'platform' ELSE 'merchant' END AS actor_realm,action,from_version,to_version,create_time FROM promotion_seckill_activity_audit "
            +"WHERE tenant_id=#{tenant} AND activity_id=#{activity} ORDER BY id DESC LIMIT #{offset},#{limit}")
    List<AuditRow> audits(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("offset") int offset,@Param("limit") int limit);

    @Data class ActivityRow {
        private Long id;private Long tenantId;private String name;private String productName;private Integer status;
        private Integer generation;private Long version;private LocalDateTime startTime;private LocalDateTime endTime;
    }
    @Data class AuditRow {
        private Long id;private Long actorId;private String actorRealm;private String action;private Long fromVersion;private Long toVersion;private LocalDateTime createTime;
    }
}
