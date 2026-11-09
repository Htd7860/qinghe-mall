package cn.qinghe.mall.module.product.service.catalog;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.module.product.dal.mysql.catalog.ProductCatalogControlMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 仅复用低频商品配置共同写边界，避免并发分类迁移/删除与SPU引用检查互相穿透。 */
@Service
public class ProductCatalogTransactions {
    private final ProductCatalogControlMapper mapper;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final TransactionTemplate transaction;
    private final cn.qinghe.mall.module.product.service.search.ProductSearchChanges searchChanges;

    public ProductCatalogTransactions(ProductCatalogControlMapper mapper, SecurityFrameworkService security,
                                      RemoteSystemSecurityClient remote, PlatformTransactionManager manager,
                                      cn.qinghe.mall.module.product.service.search.ProductSearchChanges searchChanges) {
        this.mapper = mapper;
        this.security = security;
        this.remote = remote;
        this.searchChanges = searchChanges;
        this.transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
    }

    public LoginUser actor() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getId() <= 0) { throw exception0(401, "请先登录"); }
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType()) || user.getTenantId() == null
                || user.getTenantId() <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(user.getTenantId(), TenantContextHolder.getTenantId())) {
            throw new AccessDeniedException("商品经营资料必须属于当前商家");
        }
        return user;
    }

    public <T> T execute(String permission, Function<LoginUser, T> action) {
        LoginUser initial = actor();
        // 锁前先检查，避免无权调用者占用管理锁；取得锁后重新检查。
        if (!security.hasPermission(permission)) { throw new AccessDeniedException("没有该操作权限"); }
        try {
            return transaction.execute(status -> {
                mapper.ensureTenant(initial.getTenantId());
                if (!Objects.equals(mapper.lockTenant(initial.getTenantId()), initial.getTenantId())) {
                    throw new IllegalStateException("商品配置锁不存在");
                }
                LoginUser current = actor();
                if (!Objects.equals(current.getId(), initial.getId())
                        || !Objects.equals(current.getTenantId(), initial.getTenantId()) || !security.hasPermission(permission)) {
                    throw new AccessDeniedException("商品配置操作资格已改变");
                }
                remote.checkTenantOperation();
                T result=action.apply(current);
                // 同事务标记；不在此发ES请求。嵌套SKU保存仍共享原事务，回滚一并撤销。
                searchChanges.mark(current.getTenantId());
                return result;
            });
        } catch (DuplicateKeyException ex) {
            throw exception0(409, "名称或关联已存在，请重新读取");
        }
    }

    public void created(TenantBaseDo row, LoginUser actor) {
        row.setTenantId(actor.getTenantId());
        row.setCreator(actor.getId().toString());
        row.setUpdater(actor.getId().toString());
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(row.getCreateTime());
        row.setDeleted(false);
    }

    public void updated(TenantBaseDo row, LoginUser actor) {
        row.setUpdater(actor.getId().toString());
        row.setUpdateTime(LocalDateTime.now());
    }

    public void requireVersion(Long actual, Long expected) {
        if (expected == null || expected < 0) { throw exception0(400, "请提供当前资料版本"); }
        if (!Objects.equals(actual, expected)) { throw exception0(409, "资料已变化，请重新读取"); }
        if (actual == Long.MAX_VALUE) { throw new IllegalStateException("商品资料版本已达到上限"); }
    }

    public void audit(LoginUser actor, String action, Long id, Long from, Long to, Map<String, ?> details) {
        MybatisUtils.requireWriteTransaction();
        if (mapper.insertAudit(actor.getTenantId(), actor.getId(), action, id, from, to,
                JsonUtils.toJsonString(details)) != 1) {
            throw new IllegalStateException("商品配置审计保存失败");
        }
    }
}
