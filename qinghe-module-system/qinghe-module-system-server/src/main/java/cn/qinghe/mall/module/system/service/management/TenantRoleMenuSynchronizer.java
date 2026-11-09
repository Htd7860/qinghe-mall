package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;
@Component
@RequiredArgsConstructor
public class TenantRoleMenuSynchronizer {
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final AdminUserMapper userMapper;
    private final RoleMenuBindings bindings;
    public List<RolePlan> plan(TenantDO tenant,Set<Long> allowed,ManagementTransactions.Actor actor) {
        MybatisUtils.requireWriteTransaction();actor.requirePlatform();
        if(tenant.getId()==null || tenant.getId()<=0 || tenant.getAdminRoleId()==null || tenant.getContactUserId()==null) {
            throw new IllegalStateException("租户指定管理员引用未初始化，不能静默跳过套餐同步");
        }
        return TenantUtils.execute(tenant.getId(),()-> {
            AdminUserDO admin=userMapper.selectOne(new LambdaQueryWrapper<AdminUserDO>()
                    .eq(AdminUserDO::getTenantId,tenant.getId()).eq(AdminUserDO::getId,tenant.getContactUserId()).last("FOR SHARE"));
            if(admin==null) { throw new IllegalStateException("租户指定管理员不存在"); }
            List<RoleDO> roles=roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                    .eq(RoleDO::getTenantId,tenant.getId()).orderByAsc(RoleDO::getId).last("FOR UPDATE"));
            RoleDO designated=roles.stream().filter(r->tenant.getAdminRoleId().equals(r.getId())).findFirst().orElse(null);
            if(designated==null || !Integer.valueOf(1).equals(designated.getType())) {
                throw new IllegalStateException("租户指定管理员角色数据异常");
            }
            List<UserRoleDO> members=userRoleMapper.selectListByRoleId(designated.getId());
            if(members.size()!=1 || !tenant.getContactUserId().equals(members.get(0).getUserId())) {
                throw new IllegalStateException("租户管理员角色绑定不唯一或与指定账号不一致");
            }
            List<RolePlan> plans=new ArrayList<>();
            for(RoleDO role:roles) {
                if(role.getAuthVersion()==null || role.getAuthVersion()<0) { throw new IllegalStateException("角色版本数据异常"); }
                Set<Long> before=roleMenuMapper.selectAllByRoleIdForUpdate(role.getId()).stream()
                        .filter(r->!Boolean.TRUE.equals(r.getDeleted())).map(RoleMenuDO::getMenuId).collect(Collectors.toCollection(TreeSet::new));
                Set<Long> target=new TreeSet<>(allowed);
                if(!designated.getId().equals(role.getId())) { target.retainAll(before); }
                plans.add(new RolePlan(role,before,target));
            }
            return plans;
        });
    }
    public void apply(TenantDO tenant,List<RolePlan> plans,ManagementTransactions.Actor actor) {
        actor.requirePlatform();
        TenantUtils.execute(tenant.getId(),()-> {
            for(RolePlan plan:plans) { bindings.replace(plan.role,plan.target,actor); }
            return null;
        });
    }
    @Getter
    public static final class RolePlan {
        private final RoleDO role;private final Set<Long> before;private final Set<Long> target;
        private RolePlan(RoleDO role,Set<Long> before,Set<Long> target) { this.role=role;this.before=before;this.target=target; }
        public boolean changed() { return !before.equals(target); }
        public int added() { Set<Long> delta=new HashSet<>(target);delta.removeAll(before);return delta.size(); }
        public int removed() { Set<Long> delta=new HashSet<>(before);delta.removeAll(target);return delta.size(); }
    }
}
