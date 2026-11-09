package cn.qinghe.mall.module.system.dal.mysql.tenant;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.TenantPageReqVO;
import java.util.List;
import java.time.LocalDateTime;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.mysql.tenant
 * @Project：qinghe-mall
 * @name：TenantMapper
 * @Date：2026/10/24 14:32
 * @Filename：TenantMapper
 */

@Mapper
public interface TenantMapper extends BaseMapperX<TenantDO>{

    @Select("SELECT * FROM system_tenant WHERE id=#{id} FOR UPDATE")
    TenantDO selectLifecycleForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM system_tenant WHERE id=#{id}")
    TenantDO selectLifecycleDetail(@Param("id") Long id);

    /** 包含被删但可恢复的租户，避免套餐删除/编辑漏掉其引用。 */
    @Select("SELECT * FROM system_tenant WHERE package_id=#{packageId} ORDER BY id FOR UPDATE")
    List<TenantDO> selectAllByPackageForUpdate(@Param("packageId") Long packageId);

    @Select("SELECT COUNT(*) FROM system_tenant WHERE package_id=#{packageId}")
    Long countAllByPackage(@Param("packageId") Long packageId);

    @Select({"<script>","SELECT * FROM system_tenant WHERE deleted=#{q.deleted}",
            "<if test=\"q.code != null and q.code != ''\"> AND code LIKE CONCAT('%',#{q.code},'%')</if>",
            "<if test=\"q.name != null and q.name != ''\"> AND name LIKE CONCAT('%',#{q.name},'%')</if>",
            "<if test=\"q.status != null\"> AND status=#{q.status}</if>",
            "<if test=\"q.packageId != null\"> AND package_id=#{q.packageId}</if>",
            "ORDER BY id DESC","</script>"})
    List<TenantDO> selectManagementPage(@Param("page") Page<TenantDO> page, @Param("q") TenantPageReqVO query);

    @Update("UPDATE system_tenant SET deleted=b'0',status=1,auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE id=#{id} AND auth_version=#{version} AND deleted=b'1'")
    int restoreLifecycle(@Param("id") Long id,@Param("version") Long version,
            @Param("actor") String actor,@Param("now") LocalDateTime now);

    @Update("UPDATE system_tenant SET auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE id=#{id} AND auth_version=#{version}")
    int incrementPackageVersion(@Param("id") Long id,@Param("version") Long version,
            @Param("actor") String actor,@Param("now") LocalDateTime now);

    default TenantDO selectByCode(String code){
        if(code==null||!code.matches("[a-z0-9-]{3,32}")){
            throw new IllegalArgumentException("商家编码格式不合法");
        };
        return selectOne(new LambdaQueryWrapper<TenantDO>().eq(TenantDO::getCode,code));
    }
}
