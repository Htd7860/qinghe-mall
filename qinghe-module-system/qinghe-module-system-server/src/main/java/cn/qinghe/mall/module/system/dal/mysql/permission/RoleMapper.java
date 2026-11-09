package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RolePageReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

@Mapper
public interface RoleMapper extends BaseMapperX<RoleDO> {

    @Update("UPDATE system_role SET data_scope=#{scope}, "
            + "data_scope_dept_ids=#{deptIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, "
            + "auth_version=auth_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND auth_version=#{version} AND deleted=b'0'")
    int updateDataScopeSql(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("version") Long version, @Param("scope") Integer scope, @Param("deptIds") java.util.Set<Long> deptIds,
            @Param("actor") String actor, @Param("now") LocalDateTime now);

    /** 生命周期恢复专用，保留类型处理器并显式读取逻辑删除行。 */
    default RoleDO selectLifecycleForUpdate(Long id) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore() || id == null || id <= 0) {
            throw new IllegalArgumentException("角色生命周期查询参数不合法");
        }
        return selectLifecycleSql(tenantId, id);
    }

    @Select("SELECT * FROM system_role WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE")
    @ResultMap("mybatis-plus_RoleDO")
    RoleDO selectLifecycleSql(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Update("UPDATE system_role SET deleted=b'0', status=1, data_scope=5, data_scope_dept_ids=JSON_ARRAY(), "
            + "auth_version=auth_version+1, updater=#{actor}, update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND auth_version=#{version} AND deleted=b'1'")
    int restoreLifecycleSql(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("version") Long version, @Param("actor") String actor,
            @Param("now") LocalDateTime now);

    /** 后续授权Service必须先锁角色，再锁其全部关联；本方法不创建事务。 */
    default RoleDO selectByIdAndTenantForUpdate(Long id) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色锁查询必须处于可信租户上下文");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("角色编号必须为正数");
        }
        return selectOne(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId)
                .eq(RoleDO::getId, id)
                .last("FOR UPDATE"));
    }

    /** 条件递增，0行由Service转换为版本冲突；不能用于覆盖角色的其他字段。 */
    default int updateAuthVersion(Long id, Long expectedVersion, Long actorId, LocalDateTime updateTime) {
        MybatisUtils.requireWriteTransaction();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色版本更新必须处于可信租户上下文");
        }
        if (id == null || id <= 0 || expectedVersion == null || expectedVersion < 0
                || expectedVersion == Long.MAX_VALUE || actorId == null || actorId <= 0
                || updateTime == null) {
            throw new IllegalArgumentException("角色版本更新参数不合法");
        }
        return update(null, new LambdaUpdateWrapper<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId)
                .eq(RoleDO::getId, id)
                .eq(RoleDO::getAuthVersion, expectedVersion)
                .set(RoleDO::getAuthVersion, expectedVersion + 1)
                .set(RoleDO::getUpdater, actorId.toString())
                .set(RoleDO::getUpdateTime, updateTime));
    }

    default PageResult<RoleDO> selectPage(RolePageReqVO reqVO) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色分页必须处于可信租户上下文");
        }
        if (Boolean.TRUE.equals(reqVO.getDeleted())) {
            Page<RoleDO> page = MybatisUtils.buildPage(reqVO);
            page.setRecords(selectDeletedPageSql(page, tenantId, reqVO));
            return new PageResult<>(page.getTotal(), page.getRecords());
        }
        return selectPage(reqVO, new LambdaQueryWrapperX<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId)
                .likeIfPresent(RoleDO::getName, reqVO.getName())
                .likeIfPresent(RoleDO::getCode, reqVO.getCode())
                .eqIfPresent(RoleDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RoleDO::getCreateTime, reqVO.getCreateTime())
                .orderByAsc(RoleDO::getSort, RoleDO::getId));
    }

    @Select({"<script>", "SELECT * FROM system_role WHERE tenant_id=#{tenantId} AND deleted=b'1'",
            "<if test=\"q.name != null and q.name.trim() != ''\"> AND name LIKE CONCAT('%',#{q.name},'%')</if>",
            "<if test=\"q.code != null and q.code.trim() != ''\"> AND code LIKE CONCAT('%',#{q.code},'%')</if>",
            "<if test=\"q.status != null\"> AND status=#{q.status}</if>",
            "<if test=\"q.createTime != null and q.createTime.length > 0 and q.createTime[0] != null\"> AND create_time &gt;= #{q.createTime[0]}</if>",
            "<if test=\"q.createTime != null and q.createTime.length > 1 and q.createTime[1] != null\"> AND create_time &lt;= #{q.createTime[1]}</if>",
            "ORDER BY sort,id", "</script>"})
    @ResultMap("mybatis-plus_RoleDO")
    List<RoleDO> selectDeletedPageSql(@Param("page") Page<RoleDO> page,
            @Param("tenantId") Long tenantId, @Param("q") RolePageReqVO request);

    default RoleDO selectByIdAndTenant(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色详情必须处于可信租户上下文");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("角色编号必须为正数");
        }
        return selectOne(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId)
                .eq(RoleDO::getId, id));
    }

    default List<RoleDO> selectListByIds(Collection<Long> ids) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("角色查询必须处于可信租户上下文");
        }
        Objects.requireNonNull(ids, "角色编号集合不能为空");
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("角色编号必须为正数");
        }
        return selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId)
                .in(RoleDO::getId, ids)
                .orderByAsc(RoleDO::getSort, RoleDO::getId));
    }
}
