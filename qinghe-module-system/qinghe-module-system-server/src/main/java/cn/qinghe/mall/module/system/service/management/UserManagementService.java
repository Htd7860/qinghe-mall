package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import cn.qinghe.mall.module.system.controller.admin.user.vo.*;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.dal.mysql.dept.DeptMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantMapper;
import cn.qinghe.mall.module.system.dal.mysql.permission.UserRoleMapper;
import cn.qinghe.mall.module.system.service.user.UserDataScope;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.oauth2.AdminCredentialVersion;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final ManagementTransactions transactions;
    private final AdminUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final TenantMapper tenantMapper;
    private final DeptMapper deptMapper;
    private final TenantService tenantService;
    private final UserDataScope dataScope;
    private final ManagedAdministrators administrators;
    private final PasswordEncoder passwordEncoder;
    private final ManagementAudit audit;

    public PageResult<UserRespVO> page(UserPageReqVO query) {
        Long tenantId = readableTenant();
        Page<AdminUserDO> page = MybatisUtils.buildPage(query);
        page.setRecords(userMapper.selectManagementPageSql(page, tenantId, query, dataScope.current()));
        return new PageResult<>(page.getTotal(), page.getRecords().stream().map(UserRespVO::from).collect(Collectors.toList()));
    }
    public UserRespVO get(Long id) {
        AdminUserDO user = userMapper.selectManagementDetailSql(readableTenant(), id);
        dataScope.current().requireVisible(user);
        return UserRespVO.from(user);
    }
    private Long readableTenant() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) { throw exception0(403, "人员查询需要可信租户"); }
        if (tenantId > 0) { tenantService.validateForOperation(tenantId); }
        return tenantId;
    }

    public Long create(UserSaveReqVO body) {
        return transactions.execute("system:user:create", actor -> {
            if (body.getId() != null || body.getExpectedVersion() != null) { throw exception0(400, "创建不能指定编号或版本"); }
            validatePassword(body.getPassword());
            UserDataScope.Scope scope = dataScope.current();
            scope.requireDepartment(body.getDeptId());
            validateDepartment(body.getDeptId());
            requireCapacity(actor.getTenantId());
            AdminUserDO row = new AdminUserDO();
            row.setTenantId(actor.getTenantId()); row.setUsername(body.getUsername());
            row.setPassword(passwordEncoder.encode(body.getPassword()));
            copyProfile(row, body);
            row.setPostIds(Collections.emptySet()); row.setAuthVersion(0L); row.setCredentialVersion(0L);
            row.setCreator(actor.getId().toString()); row.setUpdater(actor.getId().toString());
            row.setCreateTime(actor.getNow()); row.setUpdateTime(actor.getNow()); row.setDeleted(false);
            if (userMapper.insert(row) != 1 || row.getId() == null || row.getId() <= 0) {
                throw new IllegalStateException("员工创建结果异常");
            }
            audit.record(actor, actor.getTenantId(), "USER.CREATE", row.getId(), -1L, 0L,
                    Collections.singletonMap("status", row.getStatus()));
            return row.getId();
        });
    }

    public Long update(UserSaveReqVO body) {
        return transactions.execute("system:user:update", actor -> {
            AdminUserDO user = editable(body.getId(), body.getExpectedVersion(), false, actor);
            if (!Objects.equals(body.getUsername(), user.getUsername())) { throw exception0(400, "登录账号创建后不可修改"); }
            if (body.getPassword() != null) { throw exception0(400, "请使用独立的重置密码接口"); }
            UserDataScope.Scope scope = dataScope.current();
            if (!Objects.equals(user.getDeptId(), body.getDeptId())) { scope.requireDepartment(body.getDeptId()); }
            validateDepartment(body.getDeptId());
            boolean disable = Integer.valueOf(1).equals(body.getStatus()) && !Objects.equals(user.getStatus(), body.getStatus());
            if (disable) { requireCanDisable(user, actor); }
            LambdaUpdateWrapper<AdminUserDO> change = change(user, actor)
                    .set(AdminUserDO::getNickname, body.getNickname()).set(AdminUserDO::getDeptId, body.getDeptId())
                    .set(AdminUserDO::getStatus, body.getStatus()).set(AdminUserDO::getRemark, body.getRemark())
                    .set(AdminUserDO::getEmail, emptyIfNull(body.getEmail())).set(AdminUserDO::getMobile, emptyIfNull(body.getMobile()))
                    .set(AdminUserDO::getSex, body.getSex() == null ? 0 : body.getSex());
            if (disable) { change.set(AdminUserDO::getCredentialVersion, nextCredential(user)); }
            requireChanged(userMapper.update(null, change));
            return recorded(actor, user, "USER.UPDATE", Collections.singletonMap("credentialsInvalidated", disable));
        });
    }

    public Long status(VersionedStatusReqVO body) {
        return transactions.execute("system:user:update", actor -> {
            AdminUserDO user = editable(body.getId(), body.getExpectedVersion(), false, actor);
            if (Objects.equals(user.getStatus(), body.getStatus())) { return user.getAuthVersion(); }
            LambdaUpdateWrapper<AdminUserDO> change = change(user, actor).set(AdminUserDO::getStatus, body.getStatus());
            if (Integer.valueOf(1).equals(body.getStatus())) {
                requireCanDisable(user, actor);
                change.set(AdminUserDO::getCredentialVersion, nextCredential(user));
            }
            requireChanged(userMapper.update(null, change));
            return recorded(actor, user, "USER.STATUS", Collections.singletonMap("status", body.getStatus()));
        });
    }

    public Long resetPassword(UserPasswordReqVO body) {
        return transactions.execute("system:user:update-password", actor -> {
            AdminUserDO user = editable(body.getId(), body.getExpectedVersion(), false, actor);
            validatePassword(body.getPassword());
            requireChanged(userMapper.update(null, change(user, actor)
                    .set(AdminUserDO::getPassword, passwordEncoder.encode(body.getPassword()))
                    .set(AdminUserDO::getCredentialVersion, nextCredential(user))));
            return recorded(actor, user, "USER.RESET_PASSWORD", Collections.singletonMap("credentialsInvalidated", true));
        });
    }

    public Long delete(VersionedIdReqVO body) {
        return transactions.execute("system:user:delete", actor -> {
            AdminUserDO user = editable(body.getId(), body.getExpectedVersion(), false, actor);
            requireCanDisable(user, actor);
            userRoleMapper.revokeByUserId(user.getId(), actor.getId(), actor.getNow());
            requireChanged(userMapper.update(null, change(user, actor).set(AdminUserDO::getDeleted, true)
                    .set(AdminUserDO::getStatus, 1).set(AdminUserDO::getCredentialVersion, nextCredential(user))));
            return recorded(actor, user, "USER.DELETE", Collections.singletonMap("bindingsAndCredentialsRevoked", true));
        });
    }

    public Long restore(VersionedIdReqVO body) {
        return transactions.execute("system:user:restore", actor -> {
            AdminUserDO user = editable(body.getId(), body.getExpectedVersion(), true, actor);
            administrators.requireOrdinaryUser(actor.getTenantId(), user.getId());
            validateDepartment(user.getDeptId());
            nextCredential(user);
            requireCapacity(actor.getTenantId());
            userRoleMapper.revokeByUserId(user.getId(), actor.getId(), actor.getNow());
            requireChanged(userMapper.restoreLifecycleSql(actor.getTenantId(), user.getId(), user.getAuthVersion(),
                    actor.getId().toString(), actor.getNow()));
            return recorded(actor, user, "USER.RESTORE", Collections.singletonMap("disabledWithoutBindingsOrSessions", true));
        });
    }

    private AdminUserDO editable(Long id, Long expectedVersion, boolean deleted, ManagementTransactions.Actor actor) {
        if (id == null || id <= 0 || expectedVersion == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE) {
            throw exception0(400, "员工编号或版本不合法");
        }
        AdminUserDO user = userMapper.selectLifecycleSql(actor.getTenantId(), id);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) != deleted) { throw exception0(404, "人员不存在"); }
        dataScope.current().requireVisible(user);
        if (!expectedVersion.equals(user.getAuthVersion())) { throw exception0(409, "人员已变化，请重新读取"); }
        return user;
    }
    private void requireCanDisable(AdminUserDO user, ManagementTransactions.Actor actor) {
        if (actor.getId().equals(user.getId())) { throw exception0(403, "不能在管理入口停用或删除自己"); }
        administrators.requireOrdinaryUser(actor.getTenantId(), user.getId());
    }
    private Long nextCredential(AdminUserDO user) {
        Long version = AdminCredentialVersion.current(user);
        if (version == Long.MAX_VALUE) { throw new IllegalStateException("账号凭据版本已达上限"); }
        return version + 1;
    }
    private void requireCapacity(Long tenantId) {
        if (tenantId == 0) { return; }
        // ManagementTransactions已锁住本租户行；所有新增/恢复共用管理互斥锁。
        TenantDO tenant = tenantMapper.selectById(tenantId);
        Long count = userMapper.selectCount(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getTenantId, tenantId));
        if (tenant == null || tenant.getAccountCount() == null || count >= tenant.getAccountCount()) {
            throw exception0(409, "后台账号额度已满，禁用账号也计入额度");
        }
    }
    private void validateDepartment(Long deptId) {
        if (deptId == null) { return; }
        Map<Long,DeptDO> rows = deptMapper.selectTenantTree().stream().collect(Collectors.toMap(DeptDO::getId,d -> d));
        Long cursor = deptId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && cursor > 0 && visited.add(cursor)) {
            DeptDO dept = rows.get(cursor);
            if (dept == null || !Integer.valueOf(0).equals(dept.getStatus())) { throw exception0(400, "所属部门或上级不存在或已停用"); }
            cursor = dept.getParentId();
        }
        if (!Long.valueOf(0).equals(cursor)) { throw exception0(400, "部门父链不完整或存在循环"); }
    }
    private void validatePassword(String value) {
        if (value == null || value.trim().isEmpty() || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.chars().anyMatch(Character::isISOControl)) {
            throw exception0(400, "密码至少8个字符，UTF-8编码不得超过72字节且不能含控制符");
        }
    }
    private void copyProfile(AdminUserDO row, UserSaveReqVO body) {
        row.setNickname(body.getNickname()); row.setDeptId(body.getDeptId()); row.setStatus(body.getStatus());
        row.setRemark(body.getRemark()); row.setEmail(emptyIfNull(body.getEmail())); row.setMobile(emptyIfNull(body.getMobile()));
        row.setSex(body.getSex() == null ? 0 : body.getSex());
    }
    private LambdaUpdateWrapper<AdminUserDO> change(AdminUserDO user, ManagementTransactions.Actor actor) {
        return new LambdaUpdateWrapper<AdminUserDO>().eq(AdminUserDO::getTenantId, actor.getTenantId())
                .eq(AdminUserDO::getId, user.getId()).eq(AdminUserDO::getAuthVersion, user.getAuthVersion())
                .set(AdminUserDO::getAuthVersion, user.getAuthVersion()+1)
                .set(AdminUserDO::getUpdater, actor.getId().toString()).set(AdminUserDO::getUpdateTime, actor.getNow());
    }
    private Long recorded(ManagementTransactions.Actor actor, AdminUserDO user, String action, Map<String,Object> details) {
        audit.record(actor, actor.getTenantId(), action, user.getId(), user.getAuthVersion(), user.getAuthVersion()+1, details);
        return user.getAuthVersion()+1;
    }
    private void requireChanged(int changed) {
        if (changed != 1) { throw exception0(409, "人员已变化，请重新读取"); }
    }
    private String emptyIfNull(String value) { return value == null ? "" : value; }
}
