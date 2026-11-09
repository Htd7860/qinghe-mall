package cn.qinghe.mall.module.product.dal.mysql.search;
import lombok.Data;
import org.apache.ibatis.annotations.*;

/** 固定搜索变更进度，不对外暴露任意更新；与商品事务一起提交。 */
@Mapper
public interface ProductSearchControlMapper {
    @Insert("INSERT INTO product_search_control(tenant_id,change_version,synced_version,initialized,update_time) "
            +"VALUES(#{tenant},0,0,b'0',UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
    int ensure(@Param("tenant") Long tenant);
    @Update("UPDATE product_search_control SET change_version=change_version+1,update_time=UTC_TIMESTAMP(6) "
            +"WHERE tenant_id=#{tenant} AND change_version<9007199254740991")
    int changed(@Param("tenant") Long tenant);
    @Select("SELECT tenant_id,change_version,synced_version,initialized,update_time FROM product_search_control WHERE tenant_id=#{tenant}")
    State read(@Param("tenant") Long tenant);
    @Data class State {
        private Long tenantId;
        private Long changeVersion;
        private Long syncedVersion;
        private Boolean initialized;
        private java.time.LocalDateTime updateTime;
    }
}
