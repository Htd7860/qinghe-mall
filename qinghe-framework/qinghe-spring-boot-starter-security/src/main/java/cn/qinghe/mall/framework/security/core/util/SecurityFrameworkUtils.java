package cn.qinghe.mall.framework.security.core.util;

import cn.hutool.core.map.MapUtil;
import cn.qinghe.mall.framework.core.util.WebFrameworkUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.core.util
 * @Project：qinghe-mall
 * @name：SecurityFrameworkUtils
 * @Date：2026/10/22 18:29
 * @Filename：SecurityFrameworkUtils
 */
public class SecurityFrameworkUtils {
    public static final String AUTHORIZATION_BEARER = "Bearer";

    public static final String LOGIN_USER_HEADER = "login-user";

    private SecurityFrameworkUtils(){}

    /** 供同时声明商家/平台路径的管理Controller核对当前端，context-path由Servlet剥离。 */
    public static void requireBackendRealm(HttpServletRequest request) {
        LoginUser user = getLoginUser();
        if (user == null) {
            throw cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0(401, "请先登录");
        }
        String path = request.getServletPath();
        Long tenantId = user.getTenantId();
        boolean realmMatches = tenantId != null && (path.startsWith("/admin-api/") && tenantId > 0
                || path.startsWith("/platform-api/") && tenantId == 0);
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType()) || !realmMatches) {
            throw new org.springframework.security.access.AccessDeniedException("后台入口与身份不匹配");
        }
    }

    public static Authentication getAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        if(context==null){
            return null;
        }
        return context.getAuthentication();
    }

    @Nullable
    public static LoginUser getLoginUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null||!authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof LoginUser ? (LoginUser) authentication.getPrincipal() : null;
    }

    @Nullable
    public static Long getLoginUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getId() : null;
    }

    @Nullable
    public static String getLoginUserNickname() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? MapUtil.getStr(loginUser.getInfo(), LoginUser.INFO_KEY_NICKNAME) : null;
    }

    @Nullable
    public static Long getLoginUserDeptId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? MapUtil.getLong(loginUser.getInfo(), LoginUser.INFO_KEY_DEPT_ID) : null;
    }

    public static void setLoginUser(
            LoginUser loginUser, HttpServletRequest request){
        UsernamePasswordAuthenticationToken authenticationToken=new UsernamePasswordAuthenticationToken(loginUser,null, Collections.emptyList());

        if(request!=null){
            authenticationToken.setDetails(new WebAuthenticationDetailsSource(). buildDetails(request));
        }

        SecurityContext context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationToken);
        SecurityContextHolder.setContext(context);

        if(request!=null){
            WebFrameworkUtils.setLoginUserId(request,loginUser.getId());
            WebFrameworkUtils.setLoginUserType(request,loginUser.getUserType());
        }
    }

    public static String obtainAuthorization(
            HttpServletRequest request, String headerName) {

        Enumeration<String> headers = request.getHeaders(headerName);
        if (headers == null || !headers.hasMoreElements()) {
            return null;
        }

        String authorization = headers.nextElement();
        if (headers.hasMoreElements()) {
            throw new BadCredentialsException("认证信息格式不正确");
        }

        if (!StringUtils.hasText(authorization)) {
            return null;
        }

        authorization = authorization.trim();
        String prefix = AUTHORIZATION_BEARER + " ";

        if (!authorization.regionMatches(
                true, 0, prefix, 0, prefix.length())) {
            throw new BadCredentialsException("认证信息格式不正确");
        }

        String token = authorization.substring(prefix.length()).trim();
        if (!StringUtils.hasText(token)
                || token.chars().anyMatch(Character::isWhitespace)
                || token.contains(",")) {
            throw new BadCredentialsException("认证信息格式不正确");
        }

        return token;
    }
}
