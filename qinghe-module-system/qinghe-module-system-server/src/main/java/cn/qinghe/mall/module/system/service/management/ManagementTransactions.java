package cn.qinghe.mall.module.system.service.management;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.ManagementMutexMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantPackageMapper;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.service.permission.PermissionService;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.user.AdminUserService;
import cn.qinghe.mall.module.system.service.oauth2.AdminCredentialVersion;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Getter;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Function;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 低频后台配置写入的共同事务入口；不用于经营业务和认证只读请求。 */
@Component
public class ManagementTransactions {
    private final TransactionTemplate transaction;
    private final ManagementMutexMapper mutexMapper;
    private final AdminUserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final AdminUserService userService;
    private final TenantService tenantService;
    private final PermissionService permissionService;

    public ManagementTransactions(PlatformTransactionManager transactionManager,
            ManagementMutexMapper mutexMapper, AdminUserMapper userMapper,
            TenantMapper tenantMapper, TenantPackageMapper packageMapper,
            AdminUserService userService, TenantService tenantService, PermissionService permissionService) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setTimeout(30);
        this.mutexMapper = mutexMapper;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.packageMapper = packageMapper;
        this.userService = userService;
        this.tenantService = tenantService;
        this.permissionService = permissionService;
    }

    public <T> T execute(String permission, Function<Actor, T> operation) {
        Objects.requireNonNull(operation);
        try {
            return transaction.execute(status -> {
                MybatisUtils.requireWriteTransaction();
                if (mutexMapper.lockManagement() == null) {
                    throw new IllegalStateException("管理事务配置未初始化");
                }
                Actor actor = authenticate(permission);
                T result = operation.apply(actor);
                // 所有管理写入持有共同锁；并发撤权不能越过此提交边界。
                if (!actor.expiresTime.isAfter(LocalDateTime.now())) {
                    throw exception0(401, "登录已失效，请重新登录");
                }
                return result;
            });
        } catch (DuplicateKeyException ex) {
            throw exception0(409, "标识或版本已存在，请重新读取后再操作");
        } catch (DataAccessException | TransactionException ex) {
            // 事务提交异常也在此边界内；不泄露SQL参数/密码编码/凭据。
            throw new IllegalStateException("管理操作结果未确认，请重新读取后再操作");
        }
    }

    private Actor authenticate(String permission) {
        LoginUser login = SecurityFrameworkUtils.getLoginUser();
        if (login == null || login.getExpiresTime() == null
                || !login.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw exception0(401, "登录已失效，请重新登录");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (TenantContextHolder.isIgnore() || tenantId < 0
                || !tenantId.equals(login.getTenantId())
                || !UserTypeEnum.ADMIN.getValue().equals(login.getUserType())) {
            throw new AccessDeniedException("后台身份与可信租户不匹配");
        }
        // 与签发保持tenant → package → user的相对顺序，锁至管理事务结束。
        if (tenantId > 0) {
            TenantDO tenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantDO>()
                    .eq(TenantDO::getId, tenantId).last("FOR UPDATE"));
            if (tenant == null) {
                throw exception0(401, "所属租户已失效");
            }
            if (packageMapper.selectOne(new LambdaQueryWrapper<TenantPackageDO>()
                    .eq(TenantPackageDO::getId, tenant.getPackageId()).last("FOR SHARE")) == null) {
                throw new IllegalStateException("租户关联套餐不存在");
            }
            tenantService.validateForOperation(tenantId);
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<AdminUserDO>()
                .eq(AdminUserDO::getTenantId, tenantId).eq(AdminUserDO::getId, login.getId())
                .last("FOR SHARE")) == null) {
            throw exception0(401, "账号已失效");
        }
        AdminCredentialVersion.requireMatches(
                userService.validateUserForAuthentication(login.getId(), tenantId), login.getInfo());
        if (permission == null || !permissionService.getUserPermission(login.getId())
                .getPermissions().contains(permission)) {
            throw new AccessDeniedException("没有该操作权限");
        }
        return new Actor(login.getId(), tenantId, login.getExpiresTime());
    }

    public static String traceId() {
        String value = MDC.get("traceId");
        return value != null && value.length() <= 64
                && value.chars().allMatch(ch -> ch >= 32 && ch <= 126) ? value : "";
    }

    /** 全局治理读取必须由平台身份发起；忽略SQL租户过滤本身不授予平台权限。 */
    public static void requirePlatformRead() {
        LoginUser login = SecurityFrameworkUtils.getLoginUser();
        if (login == null || login.getExpiresTime() == null || !login.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw exception0(401, "登录已失效，请重新登录");
        }
        if (!Long.valueOf(0).equals(TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()
                || !Long.valueOf(0).equals(login.getTenantId())
                || !UserTypeEnum.ADMIN.getValue().equals(login.getUserType())) {
            throw new AccessDeniedException("仅平台后台可读取此管理配置");
        }
    }

    @Getter
    public static final class Actor {
        private final Long id;
        private final Long tenantId;
        private final LocalDateTime now;
        private final LocalDateTime expiresTime;
        private Actor(Long id, Long tenantId, LocalDateTime expiresTime) {
            this.id = id;
            this.tenantId = tenantId;
            this.expiresTime = expiresTime;
            this.now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
        public void requirePlatform() {
            if (tenantId != 0) {
                throw new AccessDeniedException("仅平台后台可执行此操作");
            }
        }
    }
}
