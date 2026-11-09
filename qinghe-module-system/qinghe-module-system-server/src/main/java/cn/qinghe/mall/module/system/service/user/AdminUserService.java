package cn.qinghe.mall.module.system.service.user;

import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.user
 * @Project：qinghe-mall
 * @name：AdminUserService
 * @Date：2026/10/24 15:08
 * @Filename：AdminUserService
 */
public interface AdminUserService {

    /** 登录定位账号，查到记录不代表密码验证通过。 */
    AdminUserDO getUserByUsername(String username, Long tenantId);

    /** 校验已有 Token 指向的后台账号是否仍然有效。 */
    AdminUserDO validateUserForAuthentication(Long userId, Long tenantId);

    /** 使用配置的密码编码器校验原始密码，不修改或记录密码。 */
    boolean isPasswordMatch(String rawPassword, String encodedPassword);
}
