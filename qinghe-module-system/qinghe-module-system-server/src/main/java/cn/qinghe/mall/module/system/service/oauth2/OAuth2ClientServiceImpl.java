package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.qinghe.mall.module.system.dal.mysql.oauth2.OAuth2ClientMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.system.enums.ErrorCodeConstants.OAUTH2_ACCESS_TOKEN_INVALID;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2ClientServiceImpl
 * @Date：2026/10/24 13:19
 * @Filename：OAuth2ClientServiceImpl
 */
@Service
public class OAuth2ClientServiceImpl implements OAuth2ClientService{

    private final OAuth2ClientMapper clientMapper;

    public OAuth2ClientServiceImpl(OAuth2ClientMapper clientMapper) {
        this.clientMapper = clientMapper;
    }

    @Override
    public OAuth2ClientDO validateClient(String clientId, Integer userType, Long tenantId, List<String> scopes) {
        if (clientId == null || !clientId.matches("[A-Za-z0-9:_-]{1,64}")) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        if (userType == null || UserTypeEnum.valueOf(userType) == null
                || tenantId == null || tenantId < 0 || scopes == null) {
            throw new IllegalStateException("客户端校验所需身份数据不完整");
        }

        if (!validScopes(scopes)) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        OAuth2ClientDO client = clientMapper.selectByClient(clientId);
        if (client == null || Boolean.TRUE.equals(client.getDeleted())) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }
        validateConfig(client,clientId);

        if (CommonStatusEnum.isDisable(client.getStatus())) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        // 检查应用入口与身份类型、租户归属是否匹配。
        if (!matchesRealm(client.getRealm(), userType, tenantId)) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        // Token 的授权范围不能超过客户端允许的范围。
        if (!new HashSet<>(client.getScopes()).containsAll(scopes)) {
            throw exception(OAUTH2_ACCESS_TOKEN_INVALID);
        }

        return client;
    }

    private boolean matchesRealm(String realm, Integer userType, Long tenantId) {


        switch (realm) {
            case "platform":
                return UserTypeEnum.ADMIN.getValue().equals(userType)
                        && tenantId == 0L;
            case "merchant":
                return UserTypeEnum.ADMIN.getValue().equals(userType)
                        && tenantId > 0;
            case "consumer":
                return UserTypeEnum.MEMBER.getValue().equals(userType)
                        && tenantId > 0;
            default:
                throw new IllegalStateException("客户端入口配置不合法");
        }
    }

    private void validateConfig(OAuth2ClientDO client, String clientId) {
        if (!clientId.equals(client.getClientId())
                || (!CommonStatusEnum.isEnable(client.getStatus())
                && !CommonStatusEnum.isDisable(client.getStatus()))
                || !StringUtils.hasText(client.getRealm())
                || client.getAccessTokenValiditySeconds() == null
                || client.getAccessTokenValiditySeconds() <= 0
                || client.getRefreshTokenValiditySeconds() == null
                || client.getRefreshTokenValiditySeconds()
                <= client.getAccessTokenValiditySeconds()
                || !validScopes(client.getScopes())) {
            throw new IllegalStateException("客户端配置数据不合法");
        }
    }

    private boolean validScopes(List<String> scopes) {
        if (scopes == null) {
            return false;
        }

        Set<String> seen = new HashSet<>();
        for (String scope : scopes) {
            if (!StringUtils.hasText(scope)
                    || scope.chars().anyMatch(ch -> Character.isWhitespace(ch)
                    || Character.isSpaceChar(ch)
                    || Character.isISOControl(ch))
                    || !seen.add(scope)) {
                return false;
            }
        }
        return true;
    }

}
