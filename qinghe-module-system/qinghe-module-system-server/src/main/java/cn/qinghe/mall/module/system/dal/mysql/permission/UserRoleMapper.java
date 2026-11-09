package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.UserRoleDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface UserRoleMapper extends BaseMapperX<UserRoleDO> {

    default List<UserRoleDO> selectListByRoleId(Long roleId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore() || roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("角色关联查询参数不合法");
        }
        return selectList(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getTenantId, tenantId)
                .eq(UserRoleDO::getRoleId, roleId).orderByAsc(UserRoleDO::getId));
    }

    @Select("SELECT * FROM system_user_role WHERE tenant_id=#{tenantId} AND user_id=#{userId} ORDER BY id FOR UPDATE")
    List<UserRoleDO> selectAllForUpdateSql(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Update("UPDATE system_user_role SET deleted=b'0',updater=#{actor},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND user_id=#{userId} AND role_id=#{roleId} AND deleted=b'1'")
    int restoreSql(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("userId") Long userId, @Param("roleId") Long roleId,
            @Param("actor") String actor, @Param("now") LocalDateTime now);

    default int revokeByUserId(Long userId, Long actorId, LocalDateTime now) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore() || userId == null || userId <= 0) {
            throw new IllegalArgumentException("账号关联撤销参数不合法");
        }
        return update(null, new LambdaUpdateWrapper<UserRoleDO>()
                .eq(UserRoleDO::getTenantId, tenantId).eq(UserRoleDO::getUserId, userId)
                .set(UserRoleDO::getDeleted, true).set(UserRoleDO::getUpdater, actorId.toString())
                .set(UserRoleDO::getUpdateTime, now));
    }

    default int revokeByRoleId(Long roleId, Long actorId, LocalDateTime now) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore() || roleId == null || roleId <= 0
                || actorId == null || actorId <= 0 || now == null) {
            throw new IllegalArgumentException("角色人员关系撤销参数不合法");
        }
        return update(null, new LambdaUpdateWrapper<UserRoleDO>()
                .eq(UserRoleDO::getTenantId, tenantId).eq(UserRoleDO::getRoleId, roleId)
                .set(UserRoleDO::getDeleted, true).set(UserRoleDO::getUpdater, actorId.toString())
                .set(UserRoleDO::getUpdateTime, now));
    }

    default List<UserRoleDO> selectListByUserId(Long userId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色关联查询必须处于可信租户上下文");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户编号必须为正数");
        }
        return selectList(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getTenantId, tenantId)
                .eq(UserRoleDO::getUserId, userId)
                .orderByAsc(UserRoleDO::getId));
    }
}
