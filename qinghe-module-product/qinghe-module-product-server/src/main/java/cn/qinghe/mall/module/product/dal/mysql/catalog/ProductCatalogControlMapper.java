package cn.qinghe.mall.module.product.dal.mysql.catalog;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商品基础资料及后续SPU配置写入共用租户内锁；不串行化高频库存扣减。 */
@Mapper
public interface ProductCatalogControlMapper {
    @Insert("INSERT INTO product_catalog_mutex(tenant_id) VALUES(#{tenantId}) ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
    int ensureTenant(@Param("tenantId") Long tenantId);

    @Select("SELECT tenant_id FROM product_catalog_mutex WHERE tenant_id=#{tenantId} FOR UPDATE")
    Long lockTenant(@Param("tenantId") Long tenantId);

    @Insert("INSERT INTO product_catalog_audit(tenant_id,actor_user_id,action,target_id,from_version,to_version,details) "
            + "VALUES(#{tenantId},#{actorId},#{action},#{targetId},#{fromVersion},#{toVersion},#{details})")
    int insertAudit(@Param("tenantId") Long tenantId, @Param("actorId") Long actorId,
                    @Param("action") String action, @Param("targetId") Long targetId,
                    @Param("fromVersion") Long fromVersion, @Param("toVersion") Long toVersion,
                    @Param("details") String details);
}
