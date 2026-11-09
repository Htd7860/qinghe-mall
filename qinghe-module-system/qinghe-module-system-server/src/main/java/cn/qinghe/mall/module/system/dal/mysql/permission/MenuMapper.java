package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.LocalDateTime;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mapper
public interface MenuMapper extends BaseMapperX<MenuDO> {

    @Select("SELECT * FROM system_menu WHERE id=#{id} FOR UPDATE")
    MenuDO selectLifecycleForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM system_menu WHERE realm=#{realm} AND deleted=#{deleted} ORDER BY sort,id")
    List<MenuDO> selectLifecycleList(@Param("realm") String realm,@Param("deleted") boolean deleted);

    /** 平台治理专用全局引用计数，不作为商家API的数据查询入口。 */
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT COUNT(*) FROM system_role_menu WHERE menu_id=#{id} AND deleted=b'0'")
    Long countAllActiveRoleReferences(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM system_tenant_package WHERE JSON_CONTAINS(menu_ids,CAST(#{id} AS JSON))")
    Long countAllPackageReferences(@Param("id") Long id);

    @Update("UPDATE system_menu SET deleted=b'0',status=1,auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE id=#{id} AND auth_version=#{version} AND deleted=b'1'")
    int restoreLifecycle(@Param("id") Long id,@Param("version") Long version,@Param("actor") String actor,@Param("now") LocalDateTime now);

    default List<MenuDO> selectListByRealm(String realm) {
        if (!"merchant".equals(realm) && !"platform".equals(realm)) {
            throw new IllegalArgumentException("后台资源所属端不合法");
        }
        return selectList(new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getRealm, realm)
                .orderByAsc(MenuDO::getSort, MenuDO::getId));
    }

    default List<MenuDO> selectListByIdsAndRealm(Collection<Long> ids, String realm) {
        if (!"merchant".equals(realm) && !"platform".equals(realm)) {
            throw new IllegalArgumentException("后台资源所属端不合法");
        }
        Objects.requireNonNull(ids, "资源编号集合不能为空");
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("资源编号必须为正数");
        }
        return selectList(new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getRealm, realm)
                .in(MenuDO::getId, ids)
                .orderByAsc(MenuDO::getSort, MenuDO::getId));
    }
}
