package cn.qinghe.mall.module.product.dal.mysql.search;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 择取既有租户任务固定游标；仅后台全局枚举，实际搜索读写始终回到可信tenant。 */
@Mapper
public interface ProductSearchJobCursorMapper {
    String TENANTS="(SELECT tenant_id FROM product_spu UNION SELECT tenant_id FROM product_search_control) t";
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COALESCE(MAX(tenant_id),0) FROM "+TENANTS+" WHERE tenant_id BETWEEN 1 AND 9007199254740991")
    Long ceiling();
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT tenant_id FROM "+TENANTS+" WHERE tenant_id>#{after} AND tenant_id<=#{ceiling} "
            +"AND MOD(tenant_id,#{total})=#{shard} ORDER BY tenant_id LIMIT 5")
    List<Long> tenants(@Param("after") Long after,@Param("ceiling") Long ceiling,@Param("shard") int shard,@Param("total") int total);
    @InterceptorIgnore(tenantLine="true")
    @Insert("INSERT INTO product_search_job_cursor(shard_index,shard_total,last_tenant_id,ceiling_tenant_id,version) "
            +"VALUES(#{shard},#{total},0,0,0) ON DUPLICATE KEY UPDATE shard_index=shard_index")
    int ensure(@Param("shard") int shard,@Param("total") int total);
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT shard_index,shard_total,last_tenant_id,ceiling_tenant_id,version FROM product_search_job_cursor "
            +"WHERE shard_index=#{shard} AND shard_total=#{total} FOR UPDATE")
    Row lock(@Param("shard") int shard,@Param("total") int total);
    @InterceptorIgnore(tenantLine="true")
    @Update("UPDATE product_search_job_cursor SET last_tenant_id=#{after},ceiling_tenant_id=#{ceiling},version=version+1,update_time=UTC_TIMESTAMP(6) "
            +"WHERE shard_index=#{r.shardIndex} AND shard_total=#{r.shardTotal} AND version=#{r.version} AND version<9007199254740991")
    int advance(@Param("r") Row row,@Param("after") Long after,@Param("ceiling") Long ceiling);
    @Data class Row {
        private Integer shardIndex,shardTotal;
        private Long lastTenantId,ceilingTenantId,version;
    }
}
