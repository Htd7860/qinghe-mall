package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 先验证客户端入口和授权范围，再委托对应类型的真实账号检查器。 */
@Service
public class OAuth2TokenEligibilityCheckerImpl
        implements OAuth2TokenEligibilityChecker {

    private final OAuth2ClientService clientService;
    private final Map<UserTypeEnum, OAuth2UserEligibilityChecker> userCheckers;

    public OAuth2TokenEligibilityCheckerImpl(
            OAuth2ClientService clientService,
            List<OAuth2UserEligibilityChecker> userCheckers) {
        this.clientService = Objects.requireNonNull(clientService);

        Map<UserTypeEnum, OAuth2UserEligibilityChecker> mappings =
                new EnumMap<>(UserTypeEnum.class);

        for (OAuth2UserEligibilityChecker checker
                : Objects.requireNonNull(userCheckers)) {
            Objects.requireNonNull(checker);
            UserTypeEnum type = Objects.requireNonNull(checker.getUserType());
            if (mappings.put(type, checker) != null) {
                throw new IllegalStateException("同一用户类型配置了多个身份检查器");
            }
        }

        this.userCheckers = Collections.unmodifiableMap(mappings);
    }

    @Override
    public void check(Long userId, Integer userType, Long tenantId,
                      String clientId, List<String> scopes) {
        UserTypeEnum type = UserTypeEnum.valueOf(userType);
        if (userId == null || userId <= 0
                || tenantId == null || tenantId < 0 || type == null) {
            throw new IllegalStateException("资格校验所需身份数据不完整");
        }

        clientService.validateClient(clientId, userType, tenantId, scopes);

        OAuth2UserEligibilityChecker checker = userCheckers.get(type);
        if (checker == null) {
            // 任何未注册的账号来源都不能使用后台账号或空检查兜底。
            throw new IllegalStateException("该用户类型的身份校验服务尚不可用");
        }

        checker.check(userId, tenantId);
    }

    @Override
    public void check(Long userId, Integer userType, Long tenantId, String clientId,
                      List<String> scopes, Map<String, String> userInfo) {
        UserTypeEnum type = UserTypeEnum.valueOf(userType);
        if (userId == null || userId <= 0 || tenantId == null || tenantId < 0 || type == null) {
            throw new IllegalStateException("资格校验所需身份数据不完整");
        }
        clientService.validateClient(clientId, userType, tenantId, scopes);
        OAuth2UserEligibilityChecker checker = userCheckers.get(type);
        if (checker == null) {
            throw new IllegalStateException("该用户类型的身份校验服务尚不可用");
        }
        checker.check(userId, tenantId, userInfo);
    }
}
