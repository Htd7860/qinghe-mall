package cn.qinghe.mall.module.system.dal.mysql.tenant;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.ResultMap;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages.PackagePageReqVO;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

@Mapper
public interface TenantPackageMapper extends BaseMapperX<TenantPackageDO> {
    @Select("SELECT * FROM system_tenant_package WHERE id=#{id} FOR UPDATE")
    @ResultMap("mybatis-plus_TenantPackageDO")
    TenantPackageDO selectLifecycleForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM system_tenant_package WHERE id=#{id}")
    @ResultMap("mybatis-plus_TenantPackageDO")
    TenantPackageDO selectLifecycleDetail(@Param("id") Long id);

    @Select({"<script>","SELECT * FROM system_tenant_package WHERE deleted=#{q.deleted}",
            "<if test=\"q.name != null and q.name != ''\"> AND name LIKE CONCAT('%',#{q.name},'%')</if>",
            "<if test=\"q.status != null\"> AND status=#{q.status}</if>","ORDER BY id DESC","</script>"})
    @ResultMap("mybatis-plus_TenantPackageDO")
    List<TenantPackageDO> selectManagementPage(@Param("page") Page<TenantPackageDO> page,@Param("q") PackagePageReqVO query);

    @Update("UPDATE system_tenant_package SET name=#{name},status=#{status},remark=#{remark}, "
            + "menu_ids=#{menuIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, "
            + "auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE id=#{id} AND auth_version=#{version} AND deleted=b'0'")
    int updateConfiguration(@Param("id") Long id,@Param("version") Long version,@Param("name") String name,
            @Param("status") Integer status,@Param("remark") String remark,@Param("menuIds") Set<Long> menuIds,
            @Param("actor") String actor,@Param("now") LocalDateTime now);

    @Update("UPDATE system_tenant_package SET deleted=b'0',status=1,auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE id=#{id} AND auth_version=#{version} AND deleted=b'1'")
    int restoreLifecycle(@Param("id") Long id,@Param("version") Long version,@Param("actor") String actor,@Param("now") LocalDateTime now);
}
