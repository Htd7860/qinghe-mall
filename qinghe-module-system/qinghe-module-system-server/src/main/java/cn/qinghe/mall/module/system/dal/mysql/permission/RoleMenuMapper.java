package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleMenuDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

@Mapper
public interface RoleMenuMapper extends BaseMapperX<RoleMenuDO> {

    /** 先锁角色父行，再锁全部原关联并按id返回；包含逻辑删除行以便显式重授复用。 */
    default List<RoleMenuDO> selectAllByRoleIdForUpdate(Long roleId) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色资源锁查询必须处于可信租户上下文");
        }
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("角色编号必须为正数");
        }
        return selectAllIncludingDeletedForUpdateSql(tenantId, roleId);
    }

    /** 内部绑定SQL；自定义查询故意不加deleted=0，不能改成普通逻辑删除查询。 */
    @Select("SELECT id, tenant_id AS tenantId, role_id AS roleId, menu_id AS menuId, "
            + "creator, create_time AS createTime, updater, update_time AS updateTime, deleted "
            + "FROM system_role_menu WHERE tenant_id = #{tenantId} AND role_id = #{roleId} "
            + "ORDER BY id FOR UPDATE")
    List<RoleMenuDO> selectAllIncludingDeletedForUpdateSql(
            @Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /** 仅恢复已锁定的同租户/同角色原行，保持id及创建审计不变；0行由Service处理。 */
    default int restoreByIdAndRole(Long id, Long roleId, Long actorId, LocalDateTime updateTime) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色资源恢复必须处于可信租户上下文");
        }
        if (id == null || id <= 0 || roleId == null || roleId <= 0
                || actorId == null || actorId <= 0 || updateTime == null) {
            throw new IllegalArgumentException("角色资源恢复参数不合法");
        }
        return restoreDeletedBindingSql(id, tenantId, roleId, actorId.toString(), updateTime);
    }

    @Update("UPDATE system_role_menu SET deleted = b'0', updater = #{updater}, update_time = #{updateTime} "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND role_id = #{roleId} AND deleted = b'1'")
    int restoreDeletedBindingSql(@Param("id") Long id, @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId, @Param("updater") String updater,
            @Param("updateTime") LocalDateTime updateTime);

    /** 空集合短路；只撤销指定资源的有效关联，不物理删除，不修改归属字段。 */
    default int revokeByRoleIdAndMenuIds(Long roleId, Collection<Long> menuIds,
                                        Long actorId, LocalDateTime updateTime) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色资源撤销必须处于可信租户上下文");
        }
        if (roleId == null || roleId <= 0 || actorId == null || actorId <= 0 || updateTime == null) {
            throw new IllegalArgumentException("角色资源撤销参数不合法");
        }
        Objects.requireNonNull(menuIds, "资源编号集合不能为空");
        if (menuIds.isEmpty()) {
            return 0;
        }
        if (menuIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("资源编号必须为正数");
        }
        return update(null, new LambdaUpdateWrapper<RoleMenuDO>()
                .eq(RoleMenuDO::getTenantId, tenantId)
                .eq(RoleMenuDO::getRoleId, roleId)
                .eq(RoleMenuDO::getDeleted, false)
                .in(RoleMenuDO::getMenuId, menuIds)
                .set(RoleMenuDO::getDeleted, true)
                .set(RoleMenuDO::getUpdater, actorId.toString())
                .set(RoleMenuDO::getUpdateTime, updateTime));
    }

    default List<RoleMenuDO> selectListByRoleIds(Collection<Long> roleIds) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色资源查询必须处于可信租户上下文");
        }
        Objects.requireNonNull(roleIds, "角色编号集合不能为空");
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (roleIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("角色编号必须为正数");
        }
        return selectList(new LambdaQueryWrapper<RoleMenuDO>()
                .eq(RoleMenuDO::getTenantId, tenantId)
                .in(RoleMenuDO::getRoleId, roleIds)
                .orderByAsc(RoleMenuDO::getId));
    }
}
