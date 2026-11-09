package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.controller.admin.tenant.vo.TenantSaveReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.permission.*;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 商家开通事务内创建指定管理员，不调用普通员工的HTTP/权限流程。 */
@Component
@RequiredArgsConstructor
public class TenantAdminProvisioner {
    private final AdminUserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuBindings bindings;
    private final PasswordEncoder passwordEncoder;
    private final ManagementAudit audit;
    public AdminReference provision(TenantDO tenant,TenantSaveReqVO body,Set<Long> menus,ManagementTransactions.Actor actor) {
        MybatisUtils.requireWriteTransaction();actor.requirePlatform();
        String password=body.getAdminPassword();
        if(body.getAdminUsername()==null || !body.getAdminUsername().matches("[A-Za-z0-9]{4,30}")
                || password==null || password.trim().isEmpty() || password.length()<8
                || password.getBytes(StandardCharsets.UTF_8).length>72 || password.chars().anyMatch(Character::isISOControl)) {
            throw exception0(400,"开通必须提供合法管理员账号，密码至少8字符且UTF-8不超过72字节");
        }
        String encoded=passwordEncoder.encode(password);
        return TenantUtils.execute(tenant.getId(),()-> {
            AdminUserDO user=new AdminUserDO();fill(user,tenant.getId(),actor);
            user.setUsername(body.getAdminUsername());user.setPassword(encoded);
            user.setNickname(body.getAdminNickname()==null || body.getAdminNickname().trim().isEmpty()?"管理员":body.getAdminNickname());
            user.setPostIds(Collections.emptySet());user.setStatus(0);user.setAuthVersion(0L);user.setCredentialVersion(0L);
            user.setEmail("");user.setMobile("");user.setSex(0);
            if(userMapper.insert(user)!=1 || user.getId()==null || user.getId()<=0) { throw new IllegalStateException("开通管理员保存异常"); }
            audit.record(actor,tenant.getId(),"USER.CREATE",user.getId(),-1L,0L,Collections.singletonMap("designatedTenantAdmin",true));
            RoleDO role=new RoleDO();fill(role,tenant.getId(),actor);
            role.setName("租户管理员");role.setCode("tenant_admin");role.setSort(0);role.setStatus(0);role.setType(1);
            role.setRemark("平台开通时创建，权限按可信管理员引用及套餐维护");role.setDataScope(1);
            role.setDataScopeDeptIds(Collections.emptySet());role.setAuthVersion(0L);
            if(roleMapper.insert(role)!=1 || role.getId()==null || role.getId()<=0) { throw new IllegalStateException("管理员角色保存异常"); }
            audit.record(actor,tenant.getId(),"ROLE.CREATE",role.getId(),-1L,0L,Collections.singletonMap("designatedTenantAdmin",true));
            UserRoleDO link=new UserRoleDO();fill(link,tenant.getId(),actor);link.setUserId(user.getId());link.setRoleId(role.getId());
            if(userRoleMapper.insert(link)!=1) { throw new IllegalStateException("管理员角色关联保存异常"); }
            bindings.replace(role,menus,actor);
            return new AdminReference(user.getId(),role.getId());
        });
    }
    private void fill(TenantBaseDo row,Long tenantId,ManagementTransactions.Actor actor) {
        row.setTenantId(tenantId);row.setCreator(actor.getId().toString());row.setUpdater(actor.getId().toString());
        row.setCreateTime(actor.getNow());row.setUpdateTime(actor.getNow());row.setDeleted(false);
    }
    @Getter
    public static final class AdminReference {
        private final Long userId;private final Long roleId;
        private AdminReference(Long userId,Long roleId) { this.userId=userId;this.roleId=roleId; }
    }
}
