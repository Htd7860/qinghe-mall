package cn.qinghe.mall.module.system.dal.mysql.user;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Update;
import cn.qinghe.mall.module.system.controller.admin.user.vo.UserPageReqVO;
import cn.qinghe.mall.module.system.service.user.UserDataScope;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.time.LocalDateTime;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.mysql.user
 * @Project：qinghe-mall
 * @name：AdminUserMapper
 * @Date：2026/10/24 15:00
 * @Filename：AdminUserMapper
 */
@Mapper
public interface AdminUserMapper extends BaseMapperX<AdminUserDO> {
    /** 管理生命周期专用，包含逻辑删除行；调用者持有管理事务并显式固定tenant。 */
    @Select("SELECT * FROM system_users WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE")
    @ResultMap("mybatis-plus_AdminUserDO")
    AdminUserDO selectLifecycleSql(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT * FROM system_users WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0'")
    @ResultMap("mybatis-plus_AdminUserDO")
    AdminUserDO selectManagementDetailSql(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select({"<script>", "SELECT * FROM system_users WHERE tenant_id=#{tenantId} AND deleted=#{q.deleted}",
            "<if test=\"q.username != null and q.username != ''\"> AND username LIKE CONCAT('%',#{q.username},'%')</if>",
            "<if test=\"q.nickname != null and q.nickname != ''\"> AND nickname LIKE CONCAT('%',#{q.nickname},'%')</if>",
            "<if test=\"q.status != null\"> AND status=#{q.status}</if>",
            "<if test=\"q.deptId != null\"> AND dept_id=#{q.deptId}</if>",
            "<if test=\"!scope.all\"> AND (1=0",
            "<if test=\"scope.self\"> OR id=#{scope.userId}</if>",
            "<if test=\"scope.deptIds.size() > 0\"> OR dept_id IN",
            "<foreach collection=\"scope.deptIds\" item=\"deptId\" open=\"(\" close=\")\" separator=\",\">#{deptId}</foreach></if>",
            ")</if>", "ORDER BY id DESC", "</script>"})
    @ResultMap("mybatis-plus_AdminUserDO")
    List<AdminUserDO> selectManagementPageSql(@Param("page") Page<AdminUserDO> page,
            @Param("tenantId") Long tenantId, @Param("q") UserPageReqVO query,
            @Param("scope") UserDataScope.Scope scope);

    @Update("UPDATE system_users SET deleted=b'0',status=1,auth_version=auth_version+1, "
            + "credential_version=credential_version+1,updater=#{actor},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND auth_version=#{version} AND deleted=b'1'")
    int restoreLifecycleSql(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("version") Long version, @Param("actor") String actor, @Param("now") LocalDateTime now);
    default AdminUserDO selectByUsernameAndTenant(String username, Long tenantId) {
        if (username == null || !username.matches("[A-Za-z0-9]{4,30}")) {
            throw new IllegalArgumentException("账号必须为4至30位字母或数字");
        }
        if (tenantId == null || tenantId < 0) {
            throw new IllegalArgumentException("租户编号不能为负数或空");
        }

        return TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapper<AdminUserDO>()
                        .eq(AdminUserDO::getUsername, username)
                        .eq(AdminUserDO::getTenantId, tenantId)));
    }


    default AdminUserDO selectByIdAndTenant(Long userId, Long tenantId){
        if(userId==null||userId<=0||tenantId==null||tenantId<0){
            throw new IllegalArgumentException("用户id或者租户id参数错误");
        }

        return TenantUtils.executeIgnore(()->selectOne(new LambdaQueryWrapper<AdminUserDO>()
                .eq(AdminUserDO::getTenantId,tenantId).eq(AdminUserDO::getId,userId)));
    };
}
