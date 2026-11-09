package cn.qinghe.mall.module.system.service.user;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.OAUTH2_ACCESS_TOKEN_INVALID;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.user
 * @Project：qinghe-mall
 * @name：AdminUserServiceImpl
 * @Date：2026/10/24 15:09
 * @Filename：AdminUserServiceImpl
 */
@Service
public class AdminUserServiceImpl implements AdminUserService{

    private final AdminUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(AdminUserMapper userMapper,
                                PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AdminUserDO getUserByUsername(String username, Long tenantId) {
        return userMapper.selectByUsernameAndTenant(username, tenantId);
    }

    @Override
    public AdminUserDO validateUserForAuthentication(Long userId, Long tenantId) {
        if (userId == null || userId <= 0
                || tenantId == null || tenantId < 0) {
            throw new IllegalStateException("账号校验所需身份数据不完整");
        }

        AdminUserDO user = userMapper.selectByIdAndTenant(userId, tenantId);

        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        if (!userId.equals(user.getId())
                || !tenantId.equals(user.getTenantId())
                || (!CommonStatusEnum.isEnable(user.getStatus())
                && !CommonStatusEnum.isDisable(user.getStatus()))
                || user.getAuthVersion() == null
                || user.getAuthVersion() < 0) {
            throw new IllegalStateException("账号身份或状态数据异常");
        }

        if (CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        return user;
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        // BCrypt 的长度限制按字节计算，不能只比较 Java 字符数。
        if (rawPassword == null || rawPassword.isEmpty()
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            throw new IllegalStateException("账号密码配置异常");
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
