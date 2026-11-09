package cn.qinghe.mall.module.infra.dal.mysql.file;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 当前为保守引用登记；未实现跨服务解绑定协议前，不自动清理旧引用。 */
@Mapper
public interface FileReferenceMapper {
    @Select("SELECT COUNT(*) FROM infra_file_reference WHERE tenant_id=#{tenantId} AND file_id=#{fileId}")
    Long countFile(@Param("tenantId") Long tenantId, @Param("fileId") Long fileId);

    @Select("SELECT COUNT(*) FROM infra_file_reference WHERE tenant_id=#{tenantId} AND file_id=#{fileId} "
            + "AND resource_type=#{type} AND resource_id=#{resourceId}")
    Long countResource(@Param("tenantId") Long tenantId, @Param("fileId") Long fileId,
                       @Param("type") String type, @Param("resourceId") Long resourceId);

    @Insert("INSERT IGNORE INTO infra_file_reference(tenant_id,file_id,resource_type,resource_id,actor_user_id) "
            + "VALUES(#{tenantId},#{fileId},#{type},#{resourceId},#{actorId})")
    int bind(@Param("tenantId") Long tenantId, @Param("fileId") Long fileId,
             @Param("type") String type, @Param("resourceId") Long resourceId, @Param("actorId") Long actorId);
}
