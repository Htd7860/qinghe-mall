package cn.qinghe.mall.framework.core.util;

import cn.hutool.core.util.NumberUtil;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.core.util
 * @Project：qinghe-mall
 * @name：WebFrameworkUtils
 * @Date：2026/10/22 15:45
 * @Filename：WebFrameworkUtils
 */
public class WebFrameworkUtils {
    public static final String HEADER_TENANT_ID = "tenant-id";
    private static final String REQUEST_ATTRIBUTE_LOGIN_USER_ID = "login_user_id";
    private static final String REQUEST_ATTRIBUTE_LOGIN_USER_TYPE = "login_user_type";

    public static Long getTenantId(HttpServletRequest request){
        String value=request.getHeader(HEADER_TENANT_ID);
        if(value==null||value.trim().isEmpty()){
            return null;
        }

        value=value.trim();
        if(!value.matches("[0-9]+")){
            throw new IllegalArgumentException("租户编号格式不对");
        }

        Long tenantId;
        try {
            tenantId=Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("租户编号格式不正确");
        }

        // 0是平台身份的合法归属；这里只解析编号，身份和端隔离由安全链校验。
        if(tenantId<0){
            throw new IllegalArgumentException("租户编号格式不正确");
        }

        return tenantId;
    }


    public static void setLoginUserId(ServletRequest request,Long userId){
        request.setAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_ID,userId);
    }

    public static void setLoginUserType(ServletRequest request, Integer userType) {
        request.setAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_TYPE, userType);
    }

    public static Long getLoginUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return (Long) request.getAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_ID);
    }

    public static Integer getLoginUserType(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        // 1. 优先，从 Attribute 中获取
        Integer userType = (Integer) request.getAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_TYPE);
        if (userType != null) {
            return userType;
        }

        return null;
    }

}
