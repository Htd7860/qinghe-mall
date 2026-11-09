package cn.qinghe.mall.framework.security.core.rpc;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.PermissionCheckReqDTO;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.framework.security.config.RemoteSecurityProperties;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 两个既有CommonApi的远程适配，不缓存身份或权限，不提供故障放行/自动重试。 */
@Slf4j
public class RemoteSystemSecurityClient {
    private final SystemSecurityFeignApi api;
    private final SecurityProperties securityProperties;
    private final Semaphore calls;

    public RemoteSystemSecurityClient(SystemSecurityFeignApi api, RemoteSecurityProperties properties,
                                      SecurityProperties securityProperties) {
        URI base = URI.create(properties.getSystemBaseUrl());
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("system认证地址配置不正确");
        }
        this.api = api;
        this.securityProperties = securityProperties;
        this.calls = new Semaphore(properties.getMaxConcurrentCalls());
    }

    public CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(String token) {
        if (token == null || token.isEmpty() || token.length() > 128 || token.chars().anyMatch(ch ->
                Character.isWhitespace(ch) || Character.isSpaceChar(ch) || Character.isISOControl(ch) || ch == ',')) {
            return CommonResult.error(401, "访问令牌格式不正确");
        }
        return call("security.token", () -> api.checkToken("Bearer " + token));
    }

    public CommonResult<Boolean> hasAnyPermissions(Long userId, Long tenantId, String... permissions) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getId(), userId)
                || !Objects.equals(user.getTenantId(), tenantId) || TenantContextHolder.isIgnore()
                || !Objects.equals(tenantId, TenantContextHolder.getTenantId())
                || permissions == null || permissions.length == 0 || permissions.length > 100) {
            return CommonResult.success(false);
        }
        for (String permission : permissions) {
            if (permission == null || permission.trim().isEmpty() || permission.length() > 128) {
                return CommonResult.success(false);
            }
        }
        String token = currentToken();
        PermissionCheckReqDTO body = new PermissionCheckReqDTO();
        body.setPermissions(Arrays.asList(permissions.clone()));
        CommonResult<Boolean> result = call("security.permission", () -> api.checkPermission("Bearer " + token, body));
        if (result == null || result.getCode() == null || !result.isSuccess() || result.getData() == null) {
            throw exception0(503, "权限检查服务暂不可用");
        }
        return result;
    }

    /** 仅普通经营写入调用；停用/到期不与认证资格混为一谈。 */
    public void checkTenantOperation() {
        String token = currentToken();
        CommonResult<Boolean> result = call("security.tenantOperation", () -> api.checkTenantOperation("Bearer " + token));
        if (result == null || result.getCode() == null) {
            throw exception0(503, "租户经营资格服务暂不可用");
        }
        result.checkError();
        if (!Boolean.TRUE.equals(result.getData())) {
            throw exception0(403, "当前租户不允许经营操作");
        }
    }

    /** 每个业务操作重新读取；与接口权限分开，不能把平台身份当商家全范围。 */
    public UserDataScopeRespDTO currentDataScope() {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (actor == null || actor.getTenantId() == null || actor.getTenantId() <= 0
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())) {
            throw exception0(403, "数据范围身份异常");
        }
        String token = currentToken();
        CommonResult<UserDataScopeRespDTO> result = call("security.dataScope", () -> api.currentDataScope("Bearer " + token));
        if (result == null || result.getCode() == null) { throw exception0(503, "数据范围服务暂不可用"); }
        result.checkError();
        UserDataScopeRespDTO scope = result.getData();
        if (scope == null || !Objects.equals(scope.getTenantId(), actor.getTenantId())
                || !Objects.equals(scope.getUserId(), actor.getId()) || scope.getAll() == null || scope.getSelf() == null
                || scope.getDeptIds() == null || scope.getDeptIds().stream().anyMatch(id -> id == null || id <= 0)
                || (scope.getCurrentDeptId() != null && scope.getCurrentDeptId() <= 0)) {
            throw exception0(503, "数据范围结果不完整");
        }
        return scope;
    }

    /** 只取同租户可见员工的真实归属，原Bearer和专用权限由system再次检查。 */
    public cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO productOwner(Long id) {
        if (id==null || id<=0) { throw exception0(400,"目标员工编号不正确"); }
        UserDataScopeRespDTO scope=currentDataScope();
        CommonResult<cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO> result=
                call("security.productOwner", () -> api.productOwner("Bearer "+currentToken(),id));
        if (result==null || result.getCode()==null) { throw exception0(503,"员工归属服务暂不可用"); }
        result.checkError();
        cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO owner=result.getData();
        if (owner==null || !Objects.equals(owner.getTenantId(),scope.getTenantId()) || !Objects.equals(owner.getOwnerUserId(),id)
                || owner.getUserVersion()==null || owner.getUserVersion()<0 || owner.getOwnerName()==null
                || (owner.getOwnerDeptId()!=null && (owner.getOwnerDeptId()<=0 || owner.getDeptVersion()==null || owner.getDeptVersion()<0))
                || (owner.getOwnerDeptId()==null && owner.getDeptVersion()!=null)) {
            throw exception0(503,"员工归属结果不完整");
        }
        if (!scope.includes(owner.getOwnerUserId(),owner.getOwnerDeptId())) { throw exception0(404,"目标员工不在可管理范围内"); }
        return owner;
    }

    private String currentToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            throw exception0(503, "资格检查缺少请求上下文");
        }
        String token = SecurityFrameworkUtils.obtainAuthorization(
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest(),
                securityProperties.getTokenHeader());
        if (token == null) { throw exception0(401, "请先登录"); }
        return token;
    }

    private <T> T call(String operation, Supplier<T> action) {
        if (!calls.tryAcquire()) {
            log.debug("RPC diagnostic operation={} outcome=capacity_rejected", operation);
            throw exception0(503, "认证服务繁忙，请稍后重试");
        }
        long started = System.nanoTime();
        try {
            T result = action.get();
            log.debug("RPC diagnostic operation={} outcome=response code={}", operation,
                    result instanceof CommonResult ? ((CommonResult<?>) result).getCode() : null);
            return result;
        } catch (RuntimeException ex) {
            log.debug("RPC diagnostic operation={} outcome=failed cause={}", operation, ex.getClass().getName());
            throw ex;
        } finally {
            calls.release();
            log.debug("RPC diagnostic operation={} elapsedMicros={}", operation, (System.nanoTime() - started) / 1000);
        }
    }
}
