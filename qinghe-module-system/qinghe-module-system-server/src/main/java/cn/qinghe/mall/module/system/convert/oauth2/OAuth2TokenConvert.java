package cn.qinghe.mall.module.system.convert.oauth2;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.qinghe.mall.module.system.dal.redis.oauth2.dto.OAuth2AccessTokenCacheDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.convert.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2TokenConvert
 * @Date：2026/10/24 10:47
 * @Filename：OAuth2TokenConvert
 */
public final class OAuth2TokenConvert {
    private OAuth2TokenConvert(){}

    public static OAuth2AccessTokenCacheDTO toCacheDTO(OAuth2AccessTokenDO source, LocalDateTime checkedAt){
        Objects.requireNonNull(source, "令牌记录不能为空");
        Objects.requireNonNull(checkedAt, "检查时间不能为空");

        OAuth2AccessTokenCacheDTO target = new OAuth2AccessTokenCacheDTO();
        target.setUserId(source.getUserId());
        target.setUserType(source.getUserType());
        target.setTenantId(source.getTenantId());
        target.setUserInfo(source.getUserInfo() == null
                ? null : new HashMap<>(source.getUserInfo()));
        target.setClientId(source.getClientId());
        target.setScopes(source.getScopes() == null
                ? null : new ArrayList<>(source.getScopes()));
        target.setExpiresTime(source.getExpiresTime());
        target.setRevokedTime(source.getRevokedTime());
        target.setCheckedAt(checkedAt);
        return target;
    }

    public static OAuth2AccessTokenCheckRespDTO toCheckRespDTO(
            OAuth2AccessTokenCacheDTO source) {
        Objects.requireNonNull(source, "令牌快照不能为空");

        OAuth2AccessTokenCheckRespDTO target = new OAuth2AccessTokenCheckRespDTO();
        target.setUserId(source.getUserId());
        target.setUserType(source.getUserType());
        target.setTenantId(source.getTenantId());
        target.setUserInfo(source.getUserInfo() == null
                ? null : new HashMap<>(source.getUserInfo()));
        target.setScopes(source.getScopes() == null
                ? null : new ArrayList<>(source.getScopes()));
        target.setExpiresTime(source.getExpiresTime());
        return target;
    }
}
