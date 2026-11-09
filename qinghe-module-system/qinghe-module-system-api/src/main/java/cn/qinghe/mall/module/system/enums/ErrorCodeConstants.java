package cn.qinghe.mall.module.system.enums;

import cn.qinghe.mall.framework.common.exception.ErrorCode;
import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.enums
 * @Project：qinghe-mall
 * @name：ErrorCodeConstants
 * @Date：2026/10/24 10:37
 * @Filename：ErrorCodeConstants
 */
public interface ErrorCodeConstants {
    ErrorCode ROLE_NOT_EXISTS = new ErrorCode(
            GlobalErrorCodeConstants.NOT_FOUND.getCode(), "角色不存在");

    ErrorCode OAUTH2_REFRESH_TOKEN_INVALID = new ErrorCode(
            GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
            "刷新令牌无效或已失效，请重新登录");

    ErrorCode OAUTH2_ACCESS_TOKEN_INVALID = new ErrorCode(
        GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
        "访问令牌无效");

    ErrorCode OAUTH2_ACCESS_TOKEN_EXPIRED = new ErrorCode(
            GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
            "访问令牌已过期");

    ErrorCode OAUTH2_ACCESS_TOKEN_REVOKED = new ErrorCode(
            GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
            "访问令牌已撤销");

    ErrorCode OAUTH2_TENANT_INVALID = new ErrorCode(
            GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
            "令牌所属租户已失效");

    ErrorCode TENANT_OPERATION_DISABLED = new ErrorCode(
            GlobalErrorCodeConstants.FORBIDDEN.getCode(),
            "租户已停用，不能进行普通经营操作");

    ErrorCode TENANT_OPERATION_EXPIRED = new ErrorCode(
            GlobalErrorCodeConstants.FORBIDDEN.getCode(),
            "租户已到期，不能进行普通经营操作");

    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = new ErrorCode(
            GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
            "账号或密码错误，或账号当前不可用");
}
