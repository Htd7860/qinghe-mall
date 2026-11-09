package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2TokenService
 * @Date：2026/10/24 10:28
 * @Filename：OAuth2TokenService
 */
public interface OAuth2TokenService {
    OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken);
}
