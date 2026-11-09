package cn.qinghe.mall.module.system.dal.mysql.dept;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface DeptMapper extends BaseMapperX<DeptDO> {
    @Select("SELECT * FROM system_dept WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE")
    DeptDO selectLifecycleSql(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT * FROM system_dept WHERE tenant_id=#{tenantId} AND deleted=#{deleted} ORDER BY sort,id")
    List<DeptDO> selectLifecycleListSql(@Param("tenantId") Long tenantId, @Param("deleted") boolean deleted);

    @Update("UPDATE system_dept SET deleted=b'0',status=1,auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND auth_version=#{version} AND deleted=b'1'")
    int restoreSql(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("version") Long version,
            @Param("actor") String actor, @Param("now") LocalDateTime now);
    default List<DeptDO> selectTenantTree() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("部门查询需要可信租户");
        }
        return selectList(new LambdaQueryWrapper<DeptDO>().eq(DeptDO::getTenantId, tenantId)
                .orderByAsc(DeptDO::getSort, DeptDO::getId));
    }
}
